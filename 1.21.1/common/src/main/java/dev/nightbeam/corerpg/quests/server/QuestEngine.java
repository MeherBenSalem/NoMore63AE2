package dev.nightbeam.corerpg.quests.server;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.api.CoreQuestsEvents;
import dev.nightbeam.corerpg.quests.book.AutoclaimMode;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.book.RewardTable;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import dev.nightbeam.corerpg.quests.item.LootCrateItem;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.progress.QuestLogic;
import dev.nightbeam.corerpg.quests.progress.QuestRecord;
import dev.nightbeam.corerpg.quests.progress.TeamProgress;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardContext;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of the quest book: progressing tasks, completing quests (and what that unlocks), claiming rewards and
 * resetting repeatable quests. All progress changes go through here.
 */
public final class QuestEngine {
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    private final ServerQuests sq;
    private final Set<UUID> inventoryChecks = new LinkedHashSet<>();
    private int tickCounter;
    private int depth;

    QuestEngine(ServerQuests sq) {
        this.sq = sq;
    }

    // ---------------------------------------------------------------- Events

    /** Delivers a game event to every task listening for it that the player's team can progress. */
    public void trigger(ServerPlayer player, TriggerEvent event) {
        if (player instanceof net.minecraft.server.level.ServerPlayer && player.isSpectator() && event.kind() != TriggerKind.STAGE) {
            return;
        }
        QuestBook book = sq.book();
        List<Task> tasks = book.tasksFor(event.kind());
        if (tasks.isEmpty()) {
            return;
        }
        Team team = sq.teamOf(player);
        TeamProgress progress = sq.progress(team);
        QuestLogic logic = new QuestLogic(book, progress);
        for (Task task : tasks) {
            if (logic.canProgress(task)) {
                task.onTrigger(new Ctx(team, progress, task, player), event);
            }
        }
    }

    /** Called when a player's inventory changes; the scan runs once at the end of the tick. */
    public void scheduleInventoryCheck(ServerPlayer player) {
        if (!sq.book().tasksFor(TriggerKind.INVENTORY).isEmpty()) {
            inventoryChecks.add(player.getUUID());
        }
    }

    public void onKill(ServerPlayer player, Entity killed) {
        trigger(player, new TriggerEvent.Kill(killed));
        dropLootCrate(killed);
    }

    void tick() {
        tickCounter++;
        if (!inventoryChecks.isEmpty()) {
            List<UUID> ids = new ArrayList<>(inventoryChecks);
            inventoryChecks.clear();
            for (UUID id : ids) {
                ServerPlayer player = sq.server().getPlayerList().getPlayer(id);
                if (player != null) {
                    trigger(player, new TriggerEvent.Inventory());
                }
            }
        }
        QuestBook book = sq.book();
        int delay = Math.max(1, book.detectionDelay);
        if (book.pollingTasks().isEmpty()) {
            return;
        }
        for (ServerPlayer player : sq.server().getPlayerList().getPlayers()) {
            // Spread players over the delay window so a full server does not poll everyone on one tick.
            if ((tickCounter + (player.getId() & 0x7FFFFFFF)) % delay != 0 || player.isSpectator()) {
                continue;
            }
            Team team = sq.teamOf(player);
            TeamProgress progress = sq.progress(team);
            QuestLogic logic = new QuestLogic(book, progress);
            for (Task task : book.pollingTasks()) {
                if (logic.canProgress(task)) {
                    task.poll(new Ctx(team, progress, task, player));
                }
            }
        }
    }

    /** Checks every task the player's team can progress against the current game state (join, team change, unlocks). */
    public void recheckAll(ServerPlayer player) {
        Team team = sq.teamOf(player);
        TeamProgress progress = sq.progress(team);
        QuestBook book = sq.book();
        for (Quest quest : book.allQuests()) {
            QuestLogic logic = new QuestLogic(book, progress);
            if (logic.isCompleted(quest)) {
                continue;
            }
            for (Task task : quest.tasks) {
                if (logic.canProgress(task)) {
                    task.recheck(new Ctx(team, progress, task, player));
                }
            }
            checkQuest(team, progress, quest, player);
        }
    }

    // ---------------------------------------------------------------- Progress

    /** Sets a task's progress for a team and runs the completion cascade. */
    public void setTaskProgress(Team team, TeamProgress progress, Task task, long value, @Nullable ServerPlayer actor) {
        long clamped = Math.max(0, Math.min(value, task.maxProgress()));
        long before = progress.taskProgress(task.id());
        if (clamped == before) {
            return;
        }
        progress.setTaskProgress(task.id(), clamped);
        sq.markSync(team);
        Quest quest = task.quest;
        if (quest == null) {
            return;
        }
        if (before == 0 && clamped > 0) {
            QuestRecord record = progress.recordOrCreate(quest.id());
            if (record.started == 0) {
                record.started = System.currentTimeMillis();
                CoreQuestsEvents.QUEST_STARTED.invoker().onQuestStarted(team, quest, actor);
            }
        }
        if (clamped >= task.maxProgress() && before < task.maxProgress()) {
            CoreQuestsEvents.TASK_COMPLETED.invoker().onTaskCompleted(team, task, actor);
            if (quest.tasks.size() > 1) {
                QuestLogic logic = new QuestLogic(sq.book(), progress);
                if (!logic.areTasksComplete(quest)) {
                    notifyTeam(team, Payloads.NotifyKind.TASK_COMPLETED, task, task.displayTitle(), quest.displayTitle(), task.displayIcon());
                }
            }
            checkQuest(team, progress, quest, actor);
        }
    }

    /** Completes the quest if its tasks and dependencies are done. */
    public void checkQuest(Team team, TeamProgress progress, Quest quest, @Nullable ServerPlayer actor) {
        QuestLogic logic = new QuestLogic(sq.book(), progress);
        if (logic.isCompleted(quest) || progress.locked || logic.isExcluded(quest) || logic.isOnCooldown(quest)) {
            return;
        }
        if (!logic.areTasksComplete(quest) || !logic.areDependenciesMet(quest)) {
            return;
        }
        completeQuest(team, progress, quest, actor, true);
    }

    private void completeQuest(Team team, TeamProgress progress, Quest quest, @Nullable ServerPlayer actor, boolean announce) {
        if (depth > 64) {
            CoreQuests.LOG.warn("Quest completion chain is too deep at {}; stopping", quest.id());
            return;
        }
        depth++;
        try {
            long now = System.currentTimeMillis();
            QuestRecord record = progress.recordOrCreate(quest.id());
            if (record.started == 0) {
                record.started = now;
            }
            record.completed = now;
            record.timesCompleted++;
            record.cooldownUntil = 0;
            for (Task task : quest.tasks) {
                if (progress.taskProgress(task.id()) < task.maxProgress()) {
                    progress.setTaskProgress(task.id(), task.maxProgress());
                }
            }
            sq.markSync(team);
            if (CoreConfig.SERVER.logCompletions.get()) {
                CoreQuests.LOG.info("Team {} completed quest {} ({})", team.name(), quest.id(), TextUtil.plain(quest.displayTitle().getString()));
            }
            CoreQuestsEvents.QUEST_COMPLETED.invoker().onQuestCompleted(team, quest, actor);
            if (announce && !quest.disableCompletionToast) {
                notifyTeam(team, Payloads.NotifyKind.QUEST_COMPLETED, quest, quest.displayTitle(), Component.translatable("corerpg_quests.toast.quest_completed"), quest.displayIcon());
            }
            if (!quest.completionCommand.isBlank()) {
                runCompletionCommand(quest, actor, team);
            }
            autoclaim(team, quest);
            if (quest.repeatable && quest.rewards.isEmpty()) {
                resetForRepeat(team, progress, quest);
            }
            checkChapter(team, progress, quest.chapter(), actor, announce);
            onQuestStateChanged(team, progress, quest, actor);
        } finally {
            depth--;
        }
    }

    /** Re-evaluates what a completed (or reset) quest unlocks: dependents may now progress or complete. */
    private void onQuestStateChanged(Team team, TeamProgress progress, Quest quest, @Nullable ServerPlayer actor) {
        QuestBook book = sq.book();
        Set<Quest> affected = new HashSet<>(book.dependents(quest.id()));
        affected.addAll(book.dependents(quest.chapter().id()));
        if (affected.isEmpty()) {
            return;
        }
        List<ServerPlayer> online = team.onlineMembers(sq.server());
        for (Quest dependent : affected) {
            QuestLogic logic = new QuestLogic(book, progress);
            if (logic.isCompleted(dependent)) {
                continue;
            }
            for (ServerPlayer member : online) {
                for (Task task : dependent.tasks) {
                    if (logic.canProgress(task)) {
                        task.recheck(new Ctx(team, progress, task, member));
                    }
                }
            }
            checkQuest(team, progress, dependent, actor);
        }
    }

    private void checkChapter(Team team, TeamProgress progress, Chapter chapter, @Nullable ServerPlayer actor, boolean announce) {
        QuestLogic logic = new QuestLogic(sq.book(), progress);
        if (progress.chapterCompleted(chapter.id()) == 0 && logic.isChapterComplete(chapter)) {
            progress.setChapterCompleted(chapter.id(), System.currentTimeMillis());
            CoreQuestsEvents.CHAPTER_COMPLETED.invoker().onChapterCompleted(team, chapter, actor);
            if (announce) {
                notifyTeam(team, Payloads.NotifyKind.CHAPTER_COMPLETED, chapter, chapter.displayTitle(), Component.translatable("corerpg_quests.toast.chapter_completed"), chapter.displayIcon());
            }
            if (progress.chapterCompleted("book") == 0 && logic.bookFraction() >= 1.0) {
                progress.setChapterCompleted("book", System.currentTimeMillis());
                if (announce) {
                    notifyTeam(team, Payloads.NotifyKind.BOOK_COMPLETED, sq.book(), sq.book().displayTitle(), Component.translatable("corerpg_quests.toast.book_completed"), sq.book().displayIcon());
                }
            }
        }
    }

    private void runCompletionCommand(Quest quest, @Nullable ServerPlayer actor, Team team) {
        String command = quest.completionCommand;
        if (actor != null) {
            command = command.replace("{p}", actor.getGameProfile().getName()).replace("{uuid}", actor.getStringUUID());
        }
        command = command.replace("{team}", team.id().toString()).replace("{quest}", quest.id());
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        var source = sq.server().createCommandSourceStack().withSuppressedOutput();
        if (actor != null) {
            source = source.withEntity(actor).withPosition(actor.position()).withLevel(actor.serverLevel());
        }
        sq.server().getCommands().performPrefixedCommand(source, command);
    }

    // ---------------------------------------------------------------- Submitting

    public void submit(ServerPlayer player, String taskId) {
        Task task = sq.book().task(taskId);
        if (task == null || !task.canSubmit()) {
            return;
        }
        Team team = sq.teamOf(player);
        TeamProgress progress = sq.progress(team);
        if (new QuestLogic(sq.book(), progress).canProgress(task)) {
            task.submit(new Ctx(team, progress, task, player));
        }
    }

    /** Progresses every custom task with this key for the player's team (negative amounts take progress away). */
    public void customTrigger(ServerPlayer player, String key, long amount) {
        trigger(player, new TriggerEvent.Custom(key, amount));
    }

    // ---------------------------------------------------------------- Rewards

    public boolean claim(ServerPlayer player, String rewardId, int choice) {
        Reward reward = sq.book().reward(rewardId);
        return reward != null && claim(player, reward, choice, true);
    }

    public boolean claim(ServerPlayer player, Reward reward, int choice, boolean notify) {
        Team team = sq.teamOf(player);
        TeamProgress progress = sq.progress(team);
        QuestLogic logic = new QuestLogic(sq.book(), progress);
        if (!logic.canClaim(reward, player.getUUID())) {
            return false;
        }
        if (reward.needsChoice() && choice < 0) {
            return false;
        }
        if (reward.isTeamReward()) {
            progress.setTeamClaimed(reward.id(), true);
        } else {
            progress.setPlayerClaimed(player.getUUID(), reward.id(), true);
        }
        sq.markSync(team);
        RewardCtx ctx = new RewardCtx(player, notify, choice);
        try {
            reward.claim(ctx);
        } catch (RuntimeException e) {
            CoreQuests.LOG.error("Reward {} failed for {}", reward.id(), player.getGameProfile().getName(), e);
        }
        ctx.flush();
        CoreQuestsEvents.REWARD_CLAIMED.invoker().onRewardClaimed(player, reward);
        Quest quest = reward.quest;
        if (quest != null && quest.repeatable && new QuestLogic(sq.book(), progress).allRewardsClaimed(quest, player.getUUID())) {
            resetForRepeat(team, progress, quest);
        }
        return true;
    }

    /** Claims everything claimable in a quest, a chapter or (empty scope) the whole book, skipping choices. */
    public int claimAll(ServerPlayer player, String scopeId) {
        QuestBook book = sq.book();
        QuestObject scope = scopeId.isEmpty() ? book : book.get(scopeId);
        List<Quest> quests = new ArrayList<>();
        switch (scope) {
            case QuestBook b -> quests.addAll(b.allQuests());
            case Chapter c -> quests.addAll(c.quests);
            case Quest q -> quests.add(q);
            case null, default -> {
                return 0;
            }
        }
        int claimed = 0;
        RewardCtx batch = new RewardCtx(player, true, -1);
        Team team = sq.teamOf(player);
        TeamProgress progress = sq.progress(team);
        for (Quest quest : quests) {
            for (Reward reward : List.copyOf(quest.rewards)) {
                if (reward.needsChoice() || (reward.excludeFromClaimAll && !(scope instanceof Quest))) {
                    continue;
                }
                QuestLogic logic = new QuestLogic(book, progress);
                if (!logic.canClaim(reward, player.getUUID())) {
                    continue;
                }
                if (reward.isTeamReward()) {
                    progress.setTeamClaimed(reward.id(), true);
                } else {
                    progress.setPlayerClaimed(player.getUUID(), reward.id(), true);
                }
                try {
                    reward.claim(batch);
                } catch (RuntimeException e) {
                    CoreQuests.LOG.error("Reward {} failed", reward.id(), e);
                }
                CoreQuestsEvents.REWARD_CLAIMED.invoker().onRewardClaimed(player, reward);
                claimed++;
            }
            if (quest.repeatable && new QuestLogic(book, progress).allRewardsClaimed(quest, player.getUUID())) {
                resetForRepeat(team, progress, quest);
            }
        }
        batch.flush();
        if (claimed > 0) {
            sq.markSync(team);
        }
        return claimed;
    }

    private void autoclaim(Team team, Quest quest) {
        List<ServerPlayer> members = team.onlineMembers(sq.server());
        for (Reward reward : List.copyOf(quest.rewards)) {
            AutoclaimMode mode = reward.effectiveAutoclaim();
            if (!mode.claims() || reward.needsChoice()) {
                continue;
            }
            for (ServerPlayer member : members) {
                claim(member, reward, -1, mode == AutoclaimMode.ENABLED);
                if (reward.isTeamReward()) {
                    break;
                }
            }
        }
    }

    /** Auto-claim rewards a player missed while offline. */
    public void autoclaimPending(ServerPlayer player) {
        Team team = sq.teamOf(player);
        QuestLogic logic = sq.logic(team);
        for (Quest quest : sq.book().allQuests()) {
            if (!logic.isCompleted(quest)) {
                continue;
            }
            for (Reward reward : List.copyOf(quest.rewards)) {
                AutoclaimMode mode = reward.effectiveAutoclaim();
                if (mode.claims() && !reward.needsChoice() && logic.canClaim(reward, player.getUUID())) {
                    claim(player, reward, -1, mode == AutoclaimMode.ENABLED);
                }
            }
        }
    }

    private void resetForRepeat(Team team, TeamProgress progress, Quest quest) {
        QuestRecord record = progress.recordOrCreate(quest.id());
        record.completed = 0;
        record.started = 0;
        record.cooldownUntil = quest.repeatCooldown > 0 ? System.currentTimeMillis() + quest.repeatCooldown * 1000L : 0;
        for (Task task : quest.tasks) {
            progress.setTaskProgress(task.id(), 0);
        }
        for (Reward reward : quest.rewards) {
            progress.clearClaims(reward.id());
        }
        progress.setChapterCompleted(quest.chapter().id(), 0);
        sq.markSync(team);
    }

    // ---------------------------------------------------------------- Admin / editor

    /** Completes a quest, chapter or the whole book for a team (rewards stay claimable). */
    public void forceComplete(Team team, QuestObject object, @Nullable ServerPlayer actor) {
        TeamProgress progress = sq.progress(team);
        for (Quest quest : questsIn(object)) {
            if (!progress.isQuestCompleted(quest.id())) {
                completeQuest(team, progress, quest, actor, false);
            }
        }
        sq.markSync(team);
    }

    /** Resets a quest, chapter or the whole book for a team, including claimed rewards. */
    public void reset(Team team, QuestObject object) {
        TeamProgress progress = sq.progress(team);
        if (object instanceof QuestBook) {
            boolean locked = progress.locked;
            boolean blocked = progress.rewardsBlocked;
            progress.resetAll();
            progress.locked = locked;
            progress.rewardsBlocked = blocked;
        } else {
            for (Quest quest : questsIn(object)) {
                progress.removeRecord(quest.id());
                quest.tasks.forEach(t -> progress.setTaskProgress(t.id(), 0));
                quest.rewards.forEach(r -> progress.clearClaims(r.id()));
                progress.setChapterCompleted(quest.chapter().id(), 0);
            }
            progress.setChapterCompleted("book", 0);
        }
        sq.markSync(team);
        for (ServerPlayer member : team.onlineMembers(sq.server())) {
            recheckAll(member);
        }
    }

    private List<Quest> questsIn(QuestObject object) {
        List<Quest> list = new ArrayList<>();
        switch (object) {
            case QuestBook b -> list.addAll(b.allQuests());
            case Chapter c -> list.addAll(c.quests);
            case Quest q -> list.add(q);
            case Task t when t.quest != null -> list.add(t.quest);
            default -> {
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- Crates and emergency items

    public void openCrate(ServerPlayer player, RewardTable table) {
        RewardCtx ctx = new RewardCtx(player, true, -1);
        for (Reward reward : table.roll(player.getRandom())) {
            reward.claim(ctx);
        }
        ctx.flush();
    }

    private void dropLootCrate(Entity killed) {
        QuestBook book = sq.book();
        if (!book.lootCrateDrops || !(killed instanceof LivingEntity living) || killed instanceof Player) {
            return;
        }
        boolean boss = killed.getType().is(BOSSES);
        boolean monster = !boss && killed instanceof Enemy;
        for (RewardTable table : book.tables) {
            if (!table.lootCrate) {
                continue;
            }
            double chance = boss ? table.bossDropChance : monster ? table.monsterDropChance : table.passiveDropChance;
            if (chance > 0 && living.getRandom().nextDouble() * 100.0 < chance) {
                ItemEntity item = new ItemEntity(killed.level(), killed.getX(), killed.getY() + 0.5, killed.getZ(), LootCrateItem.of(table));
                item.setDefaultPickUpDelay();
                killed.level().addFreshEntity(item);
            }
        }
    }

    public boolean giveEmergencyItems(ServerPlayer player) {
        QuestBook book = sq.book();
        if (book.emergencyItems.isEmpty()) {
            return false;
        }
        TeamProgress progress = sq.progressOf(player);
        long now = System.currentTimeMillis();
        long readyAt = progress.emergencyUsed(player.getUUID()) + book.emergencyItemsCooldown * 1000L;
        if (now < readyAt) {
            player.displayClientMessage(Component.translatable("corerpg_quests.emergency.cooldown", TextUtil.duration((readyAt - now) / 1000)), true);
            return false;
        }
        progress.setEmergencyUsed(player.getUUID(), now);
        RewardCtx ctx = new RewardCtx(player, true, -1);
        book.emergencyItems.forEach(stack -> ctx.giveItem(stack.copy()));
        ctx.flush();
        return true;
    }

    // ---------------------------------------------------------------- Notifications

    private void notifyTeam(Team team, Payloads.NotifyKind kind, QuestObject object, Component title, Component text, Icon icon) {
        Payloads.Notify payload = new Payloads.Notify(kind.ordinal(), object.id(), title, text, icon.stack());
        for (ServerPlayer member : team.onlineMembers(sq.server())) {
            sq.send(member, payload);
        }
    }

    // ---------------------------------------------------------------- Contexts

    private final class Ctx implements TaskContext {
        private final Team team;
        private final TeamProgress progress;
        private final Task task;
        private final ServerPlayer player;

        Ctx(Team team, TeamProgress progress, Task task, ServerPlayer player) {
            this.team = team;
            this.progress = progress;
            this.task = task;
            this.player = player;
        }

        @Override
        public ServerPlayer player() {
            return player;
        }

        @Override
        public long progress() {
            return progress.taskProgress(task.id());
        }

        @Override
        public void setProgress(long value) {
            setTaskProgress(team, progress, task, value, player);
        }

        @Override
        public Set<String> stages() {
            return team.stages();
        }

        @Override
        public boolean isQuestCompleted(String questId) {
            return progress.isQuestCompleted(questId);
        }
    }

    private final class RewardCtx implements RewardContext {
        private final ServerPlayer player;
        private final boolean notify;
        private final int choice;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<Component> lines = new ArrayList<>();

        RewardCtx(ServerPlayer player, boolean notify, int choice) {
            this.player = player;
            this.notify = notify;
            this.choice = choice;
        }

        @Override
        public ServerPlayer player() {
            return player;
        }

        @Override
        public boolean shouldNotify() {
            return notify;
        }

        @Override
        public void giveItem(ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }
            ItemStack shown = stack.copy();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            for (ItemStack existing : items) {
                if (ItemStack.isSameItemSameComponents(existing, shown)) {
                    existing.grow(shown.getCount());
                    return;
                }
            }
            if (items.size() < 64) {
                items.add(shown);
            }
        }

        @Override
        public void announce(Component text) {
            if (lines.size() < 32) {
                lines.add(text);
            }
        }

        @Override
        public void toast(Component title, Component description, Icon icon) {
            if (notify) {
                sq.send(player, new Payloads.Notify(Payloads.NotifyKind.TOAST.ordinal(), "", title, description, icon.stack()));
            }
        }

        @Override
        public void addStage(String stage) {
            sq.teams().addStage(sq.teamOf(player), stage);
        }

        @Override
        public void removeStage(String stage) {
            sq.teams().removeStage(sq.teamOf(player), stage);
        }

        @Override
        public int choice() {
            return choice;
        }

        void flush() {
            player.containerMenu.broadcastChanges();
            if (notify && (!items.isEmpty() || !lines.isEmpty())) {
                sq.send(player, new Payloads.RewardsReceived(List.copyOf(items), List.copyOf(lines)));
            }
            items.clear();
            lines.clear();
        }
    }

    /** Placeholder icon for messages without one. */
    static ItemStack bookIcon() {
        return new ItemStack(Items.WRITABLE_BOOK);
    }
}
