package dev.nightbeam.corerpg.quests.gametest;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.AutoclaimMode;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.Tristate;
import dev.nightbeam.corerpg.quests.progress.QuestRecord;
import dev.nightbeam.corerpg.quests.progress.TeamProgress;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.types.ItemReward;
import dev.nightbeam.corerpg.quests.reward.types.StageReward;
import dev.nightbeam.corerpg.quests.server.ServerQuests;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.types.AdvancementTask;
import dev.nightbeam.corerpg.quests.task.types.BreakBlockTask;
import dev.nightbeam.corerpg.quests.task.types.CheckmarkTask;
import dev.nightbeam.corerpg.quests.task.types.ItemEventTask;
import dev.nightbeam.corerpg.quests.task.types.ItemTask;
import dev.nightbeam.corerpg.quests.task.types.KillTask;
import dev.nightbeam.corerpg.quests.task.types.LocationTask;
import dev.nightbeam.corerpg.quests.task.types.PlaceBlockTask;
import dev.nightbeam.corerpg.quests.task.types.StageTask;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.team.TeamException;
import dev.nightbeam.corerpg.quests.vein.VeinMining;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Game tests that run the real engine inside a game test server, on both loaders. They exercise the detection
 * mixins (inventory, kill, block break and place, crafting, advancements), polling, the completion cascade, rewards,
 * teams, stages, smart filters and vein mining. Each test runs in its own batch, so it can install its own book.
 */
@SuppressWarnings("removal") // makeMockServerPlayerInLevel is the supported way to get a test player in 1.21.1
public final class QuestGameTests {
    private static final String TEMPLATE = CoreQuests.MOD_ID + ":gametest";
    private static final int TIMEOUT = 100;

    private QuestGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> all() {
        List<TestFunction> tests = new ArrayList<>();
        add(tests, "item_detection", QuestGameTests::itemDetection);
        add(tests, "item_hand_in", QuestGameTests::itemHandIn);
        add(tests, "kill", QuestGameTests::kill);
        add(tests, "break_block", QuestGameTests::breakBlock);
        add(tests, "place_block", QuestGameTests::placeBlock);
        add(tests, "craft", QuestGameTests::craft);
        add(tests, "advancement", QuestGameTests::advancement);
        add(tests, "location_polling", QuestGameTests::locationPolling);
        add(tests, "dependency_cascade", QuestGameTests::dependencyCascade);
        add(tests, "reward_claim", QuestGameTests::rewardClaim);
        add(tests, "autoclaim", QuestGameTests::autoclaim);
        add(tests, "team_rewards", QuestGameTests::teamRewards);
        add(tests, "repeatable", QuestGameTests::repeatable);
        add(tests, "stage_reward_unlocks_stage_task", QuestGameTests::stageReward);
        add(tests, "smart_filter", QuestGameTests::smartFilter);
        add(tests, "vein_mining", QuestGameTests::veinMining);
        return tests;
    }

    private static void add(List<TestFunction> tests, String name, Consumer<GameTestHelper> body) {
        // One batch per test: batches run one after another, so each test may swap the book safely.
        tests.add(new TestFunction("corerpg_quests_" + name, "corerpg_quests." + name, TEMPLATE, TIMEOUT, 0, true, body));
    }

    // ---------------------------------------------------------------- Helpers

    private static ServerQuests engine(GameTestHelper h) {
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq == null) {
            ServerQuests.onServerStarting(h.getLevel().getServer());
            sq = ServerQuests.getOrNull();
        }
        return sq;
    }

    private static final class Builder {
        final QuestBook book = new QuestBook();
        final Chapter chapter = new Chapter(book);

        Builder() {
            chapter.setIdInternal("gt_chapter");
            book.chapters.add(chapter);
            book.giveBookOnFirstJoin = false;
            book.detectionDelay = 1;
        }

        Quest quest(String id, String... deps) {
            Quest q = new Quest(chapter);
            q.setIdInternal(id);
            q.dependencies = new ArrayList<>(List.of(deps));
            chapter.quests.add(q);
            return q;
        }

        <T extends Task> T task(Quest q, String id, T task) {
            task.setIdInternal(id);
            task.quest = q;
            q.tasks.add(task);
            return task;
        }

        CheckmarkTask check(Quest q, String id) {
            return task(q, id, TaskTypes.CHECKMARK.create());
        }

        ItemTask item(Quest q, String id, Item item, long count) {
            ItemTask t = task(q, id, TaskTypes.ITEM.create());
            t.item = new ItemStack(item);
            t.count = count;
            return t;
        }

        <R extends Reward> R reward(Quest q, String id, R reward) {
            reward.setIdInternal(id);
            reward.quest = q;
            q.rewards.add(reward);
            return reward;
        }

        ItemReward itemReward(Quest q, String id, Item item, int count) {
            ItemReward r = new ItemReward();
            r.item = new ItemStack(item);
            r.count = count;
            return reward(q, id, r);
        }

        /**
     * Installs the book and joins a fresh mock player (who gets their own personal team). The template's stone floor
     * is at relative y=1 (game test templates start one block above the structure block), so the player stands on y=2.
     */
        ServerPlayer install(GameTestHelper h) {
            engine(h).replaceBookForTesting(book);
            ServerPlayer player = h.makeMockServerPlayerInLevel();
            player.moveTo(h.absoluteVec(new Vec3(4.5, 2, 4.5)));
            return player;
        }
    }

    private static TeamProgress progress(ServerPlayer player) {
        ServerQuests sq = ServerQuests.getOrNull();
        return sq.progress(sq.teamOf(player));
    }

    private static boolean completed(ServerPlayer player, String questId) {
        return progress(player).isQuestCompleted(questId);
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static void leave(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            h.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        // As in the player's own tick: syncing the inventory menu fires the vanilla inventory_changed trigger.
        player.inventoryMenu.broadcastChanges();
    }

    // ---------------------------------------------------------------- Tests

    /** Carrying items completes a non-consuming item task (inventory_changed trigger mixin). */
    private static void itemDetection(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_iron");
        b.item(q, "gt_iron_t", Items.IRON_INGOT, 3);
        ServerPlayer player = b.install(h);
        give(player, new ItemStack(Items.IRON_INGOT, 2));
        h.runAtTickTime(3, () -> {
            h.assertTrue(progress(player).taskProgress("gt_iron_t") == 2, "2 of 3 ingots counted, got " + progress(player).taskProgress("gt_iron_t"));
            h.assertFalse(completed(player, "gt_iron"), "not complete with 2 of 3");
            give(player, new ItemStack(Items.IRON_INGOT, 1));
        });
        h.runAtTickTime(6, () -> {
            h.assertTrue(completed(player, "gt_iron"), "the item task completes once 3 ingots are carried");
            h.assertTrue(count(player, Items.IRON_INGOT) == 3, "detection must not take the items");
            leave(h, player);
            h.succeed();
        });
    }

    /** A consuming item task takes the items when the player hands them in. */
    private static void itemHandIn(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_gold");
        ItemTask task = b.item(q, "gt_gold_t", Items.GOLD_INGOT, 5);
        task.consumeItems = Tristate.TRUE;
        ServerPlayer player = b.install(h);
        give(player, new ItemStack(Items.GOLD_INGOT, 3));
        h.runAtTickTime(3, () -> {
            h.assertTrue(progress(player).taskProgress("gt_gold_t") == 0, "consuming tasks do not auto-detect");
            engine(h).engine().submit(player, "gt_gold_t");
            h.assertTrue(count(player, Items.GOLD_INGOT) == 0, "all 3 handed in");
            h.assertTrue(progress(player).taskProgress("gt_gold_t") == 3, "progress kept between hand-ins");
            give(player, new ItemStack(Items.GOLD_INGOT, 4));
            engine(h).engine().submit(player, "gt_gold_t");
            h.assertTrue(count(player, Items.GOLD_INGOT) == 2, "only the 2 still needed are taken, " + count(player, Items.GOLD_INGOT) + " left");
            h.assertTrue(completed(player, "gt_gold"), "quest complete after 5");
            leave(h, player);
            h.succeed();
        });
    }

    /** Killing a mob progresses a kill task through the vanilla player_killed_entity trigger. */
    private static void kill(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_kill");
        KillTask task = b.task(q, "gt_kill_t", TaskTypes.KILL.create());
        task.entity = new Ref(ResourceLocation.withDefaultNamespace("zombie"), false);
        task.value = 2;
        Quest other = b.quest("gt_kill_skeleton");
        KillTask skeleton = b.task(other, "gt_kill_skeleton_t", TaskTypes.KILL.create());
        skeleton.entity = new Ref(ResourceLocation.withDefaultNamespace("skeleton"), false);
        ServerPlayer player = b.install(h);
        for (int i = 0; i < 2; i++) {
            Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(2 + i * 2, 2, 2));
            zombie.hurt(player.damageSources().playerAttack(player), 1000f);
            h.assertTrue(zombie.isDeadOrDying(), "zombie should be dead");
        }
        h.assertTrue(completed(player, "gt_kill"), "two zombie kills complete the task");
        h.assertTrue(progress(player).taskProgress("gt_kill_skeleton_t") == 0, "zombies do not count for a skeleton task");
        leave(h, player);
        h.succeed();
    }

    /** Breaking blocks progresses a break_block task (block-breaking mixin). */
    private static void breakBlock(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_dig");
        BreakBlockTask task = b.task(q, "gt_dig_t", TaskTypes.BREAK_BLOCK.create());
        task.block = new Ref(ResourceLocation.withDefaultNamespace("dirt"), false);
        task.value = 2;
        ServerPlayer player = b.install(h);
        BlockPos a = new BlockPos(1, 2, 1);
        BlockPos c = new BlockPos(3, 2, 1);
        BlockPos stone = new BlockPos(5, 2, 1);
        h.setBlock(a, Blocks.DIRT);
        h.setBlock(c, Blocks.DIRT);
        h.setBlock(stone, Blocks.COBBLESTONE);
        h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(stone)), "break cobblestone");
        h.assertTrue(progress(player).taskProgress("gt_dig_t") == 0, "cobblestone does not count");
        h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(a)), "break dirt 1");
        h.assertTrue(progress(player).taskProgress("gt_dig_t") == 1, "one dirt counted");
        h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(c)), "break dirt 2");
        h.assertBlockPresent(Blocks.AIR, a);
        h.assertTrue(completed(player, "gt_dig"), "two dirt blocks complete the task");
        leave(h, player);
        h.succeed();
    }

    /** Placing a block progresses a place_block task (BlockItem#place mixin). */
    private static void placeBlock(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_build");
        PlaceBlockTask task = b.task(q, "gt_build_t", TaskTypes.PLACE_BLOCK.create());
        task.block = new Ref(ResourceLocation.withDefaultNamespace("oak_planks"), false);
        ServerPlayer player = b.install(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
        BlockPos floor = h.absolutePos(new BlockPos(2, 1, 2));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(2, 2, 2));
        h.assertTrue(completed(player, "gt_build"), "placing oak planks completes the task");
        leave(h, player);
        h.succeed();
    }

    /** Crafting progresses a craft task (ItemStack#onCraftedBy mixin), counting the crafted amount. */
    private static void craft(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_craft");
        ItemEventTask task = b.task(q, "gt_craft_t", TaskTypes.CRAFT.create());
        task.item = new ItemStack(Items.STICK);
        task.count = 8;
        ServerPlayer player = b.install(h);
        new ItemStack(Items.STICK, 4).onCraftedBy(h.getLevel(), player, 4);
        h.assertTrue(progress(player).taskProgress("gt_craft_t") == 4, "4 sticks counted");
        new ItemStack(Items.TORCH, 4).onCraftedBy(h.getLevel(), player, 4);
        h.assertTrue(progress(player).taskProgress("gt_craft_t") == 4, "torches do not count");
        new ItemStack(Items.STICK, 4).onCraftedBy(h.getLevel(), player, 4);
        h.assertTrue(completed(player, "gt_craft"), "8 sticks complete the task");
        leave(h, player);
        h.succeed();
    }

    /** Earning an advancement criterion completes an advancement task (PlayerAdvancements mixin). */
    private static void advancement(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_adv");
        AdvancementTask task = b.task(q, "gt_adv_t", TaskTypes.ADVANCEMENT.create());
        task.advancement = ResourceLocation.withDefaultNamespace("story/root");
        ServerPlayer player = b.install(h);
        AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(task.advancement);
        h.assertTrue(holder != null, "story/root exists");
        h.assertFalse(completed(player, "gt_adv"), "not earned yet");
        player.getAdvancements().award(holder, "crafting_table");
        h.assertTrue(completed(player, "gt_adv"), "earning the advancement completes the task");
        leave(h, player);
        h.succeed();
    }

    /** Polled tasks (location) complete when the player stands in the box. */
    private static void locationPolling(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_loc");
        LocationTask task = b.task(q, "gt_loc_t", TaskTypes.LOCATION.create());
        BlockPos corner = h.absolutePos(new BlockPos(5, 2, 5));
        task.dimension = h.getLevel().dimension().location();
        task.x = corner.getX();
        task.y = corner.getY();
        task.z = corner.getZ();
        task.width = 2;
        task.height = 3;
        task.depth = 2;
        ServerPlayer player = b.install(h);
        player.moveTo(h.absoluteVec(new Vec3(1.5, 2, 1.5)));
        h.runAtTickTime(5, () -> {
            h.assertFalse(completed(player, "gt_loc"), "outside the box");
            player.moveTo(h.absoluteVec(new Vec3(5.5, 2, 5.5)));
        });
        h.runAtTickTime(10, () -> {
            h.assertTrue(completed(player, "gt_loc"), "inside the box");
            leave(h, player);
            h.succeed();
        });
    }

    /** Completing a quest unlocks its dependents, which re-check and can complete straight away. */
    private static void dependencyCascade(GameTestHelper h) {
        Builder b = new Builder();
        Quest first = b.quest("gt_first");
        b.check(first, "gt_first_t");
        Quest second = b.quest("gt_second", "gt_first");
        b.item(second, "gt_second_t", Items.EMERALD, 1);
        Quest third = b.quest("gt_third", "gt_second");
        b.check(third, "gt_third_t");
        ServerPlayer player = b.install(h);
        give(player, new ItemStack(Items.EMERALD));
        h.runAtTickTime(4, () -> {
            h.assertFalse(completed(player, "gt_second"), "locked quests do not progress in linear mode");
            h.assertTrue(progress(player).taskProgress("gt_second_t") == 0, "no progress while locked");
            engine(h).engine().submit(player, "gt_third_t");
            h.assertFalse(completed(player, "gt_third"), "a locked checkmark cannot be ticked");
            engine(h).engine().submit(player, "gt_first_t");
            h.assertTrue(completed(player, "gt_first"), "first done");
            h.assertTrue(completed(player, "gt_second"), "second completes at once: the emerald is already carried");
            h.assertFalse(completed(player, "gt_third"), "third waits for its checkmark");
            engine(h).engine().submit(player, "gt_third_t");
            h.assertTrue(completed(player, "gt_third"), "third done");
            h.assertTrue(progress(player).chapterCompleted("gt_chapter") > 0, "chapter complete");
            leave(h, player);
            h.succeed();
        });
    }

    /** Claiming gives the reward once. */
    private static void rewardClaim(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_claim");
        b.check(q, "gt_claim_t");
        b.itemReward(q, "gt_claim_r", Items.DIAMOND, 3);
        ServerPlayer player = b.install(h);
        h.assertFalse(engine(h).engine().claim(player, "gt_claim_r", -1), "cannot claim before completion");
        engine(h).engine().submit(player, "gt_claim_t");
        h.assertTrue(engine(h).engine().claim(player, "gt_claim_r", -1), "claim works");
        h.assertTrue(count(player, Items.DIAMOND) == 3, "3 diamonds given");
        h.assertFalse(engine(h).engine().claim(player, "gt_claim_r", -1), "cannot claim twice");
        h.assertTrue(count(player, Items.DIAMOND) == 3, "still 3 diamonds");
        leave(h, player);
        h.succeed();
    }

    /** Auto-claimed rewards arrive on completion. */
    private static void autoclaim(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_auto");
        b.check(q, "gt_auto_t");
        ItemReward reward = b.itemReward(q, "gt_auto_r", Items.EMERALD, 2);
        reward.autoclaim = AutoclaimMode.ENABLED;
        ServerPlayer player = b.install(h);
        engine(h).engine().submit(player, "gt_auto_t");
        h.assertTrue(count(player, Items.EMERALD) == 2, "auto-claimed on completion");
        h.assertFalse(engine(h).engine().claim(player, "gt_auto_r", -1), "already claimed");
        leave(h, player);
        h.succeed();
    }

    /** Party members share progress; team rewards are claimed once per team, personal rewards once per player. */
    private static void teamRewards(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_team");
        b.check(q, "gt_team_t");
        ItemReward shared = b.itemReward(q, "gt_team_shared", Items.DIAMOND, 1);
        shared.teamReward = Tristate.TRUE;
        b.itemReward(q, "gt_team_personal", Items.EMERALD, 1);
        ServerPlayer p1 = b.install(h);
        ServerPlayer p2 = h.makeMockServerPlayerInLevel();
        ServerQuests sq = engine(h);
        try {
            Team party = sq.teams().createParty(p1.getUUID(), "P1", "GT Party " + p1.getUUID().toString().substring(0, 6));
            sq.teams().forceJoin(party, p2.getUUID(), "P2");
            h.assertTrue(sq.teamOf(p2) == party, "p2 is in the party");
        } catch (TeamException e) {
            throw new AssertionError(e.getMessage());
        }
        sq.engine().submit(p1, "gt_team_t");
        h.assertTrue(completed(p2, "gt_team"), "p2 shares the party's progress");
        h.assertTrue(sq.engine().claim(p1, "gt_team_shared", -1), "p1 claims the team reward");
        h.assertFalse(sq.engine().claim(p2, "gt_team_shared", -1), "the team reward is gone for p2");
        h.assertTrue(sq.engine().claim(p2, "gt_team_personal", -1), "p2 claims their personal reward");
        h.assertTrue(sq.engine().claim(p1, "gt_team_personal", -1), "p1 claims their personal reward");
        h.assertTrue(count(p1, Items.DIAMOND) == 1 && count(p2, Items.DIAMOND) == 0, "one diamond for the team");
        h.assertTrue(count(p1, Items.EMERALD) == 1 && count(p2, Items.EMERALD) == 1, "one emerald each");
        leave(h, p1, p2);
        h.succeed();
    }

    /** Repeatable quests reset after their rewards are claimed (or at once when they have none). */
    private static void repeatable(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_rep");
        q.repeatable = true;
        b.check(q, "gt_rep_t");
        b.itemReward(q, "gt_rep_r", Items.BREAD, 1);
        Quest bare = b.quest("gt_rep_bare");
        bare.repeatable = true;
        b.check(bare, "gt_rep_bare_t");
        ServerPlayer player = b.install(h);
        ServerQuests sq = engine(h);
        sq.engine().submit(player, "gt_rep_t");
        h.assertTrue(completed(player, "gt_rep"), "completed");
        h.assertTrue(sq.engine().claim(player, "gt_rep_r", -1), "claimed");
        QuestRecord record = progress(player).record("gt_rep");
        h.assertFalse(record.isCompleted(), "reset after claiming");
        h.assertTrue(record.timesCompleted == 1, "counted once");
        h.assertTrue(progress(player).taskProgress("gt_rep_t") == 0, "task reset");
        sq.engine().submit(player, "gt_rep_t");
        h.assertTrue(sq.engine().claim(player, "gt_rep_r", -1), "claimable again");
        h.assertTrue(progress(player).record("gt_rep").timesCompleted == 2, "counted twice");
        h.assertTrue(count(player, Items.BREAD) == 2, "two loaves");

        sq.engine().submit(player, "gt_rep_bare_t");
        sq.engine().submit(player, "gt_rep_bare_t");
        h.assertTrue(progress(player).record("gt_rep_bare").timesCompleted == 2, "no rewards: resets straight away");
        leave(h, player);
        h.succeed();
    }

    /** A stage reward adds a team stage, which completes a stage task elsewhere in the book. */
    private static void stageReward(GameTestHelper h) {
        Builder b = new Builder();
        Quest giver = b.quest("gt_stage_giver");
        b.check(giver, "gt_stage_giver_t");
        StageReward reward = b.reward(giver, "gt_stage_r", new StageReward());
        reward.stage = "gt_gate_open";
        Quest needs = b.quest("gt_stage_needs");
        StageTask task = b.task(needs, "gt_stage_needs_t", TaskTypes.STAGE.create());
        task.stage = "gt_gate_open";
        ServerPlayer player = b.install(h);
        ServerQuests sq = engine(h);
        h.assertFalse(completed(player, "gt_stage_needs"), "no stage yet");
        sq.engine().submit(player, "gt_stage_giver_t");
        h.assertTrue(sq.engine().claim(player, "gt_stage_r", -1), "claim the stage reward");
        h.assertTrue(sq.teamOf(player).hasStage("gt_gate_open"), "the team has the stage");
        h.assertTrue(completed(player, "gt_stage_needs"), "the stage task completed");
        leave(h, player);
        h.succeed();
    }

    /** A smart filter item in an item task accepts every item its filter matches. */
    private static void smartFilter(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_filter");
        ItemTask task = b.task(q, "gt_filter_t", TaskTypes.ITEM.create());
        ItemStack filter = new ItemStack(ModRegistry.SMART_FILTER.get());
        filter.set(ModRegistry.FILTER_COMPONENT.get(), "any(minecraft:apple, minecraft:carrot)");
        task.item = filter;
        task.count = 2;
        ServerPlayer player = b.install(h);
        give(player, new ItemStack(Items.BREAD, 5));
        give(player, new ItemStack(Items.APPLE));
        h.runAtTickTime(3, () -> {
            h.assertTrue(progress(player).taskProgress("gt_filter_t") == 1, "only the apple matches, got " + progress(player).taskProgress("gt_filter_t"));
            give(player, new ItemStack(Items.CARROT));
        });
        h.runAtTickTime(6, () -> {
            h.assertTrue(completed(player, "gt_filter"), "apple + carrot complete the filtered task");
            leave(h, player);
            h.succeed();
        });
    }

    /** Vein mining breaks the connected logs through the normal break path and gathers the drops at the origin. */
    private static void veinMining(GameTestHelper h) {
        Builder b = new Builder();
        Quest q = b.quest("gt_vein");
        BreakBlockTask task = b.task(q, "gt_vein_t", TaskTypes.BREAK_BLOCK.create());
        task.block = new Ref(ResourceLocation.withDefaultNamespace("logs"), true);
        task.value = 5;
        ServerPlayer player = b.install(h);
        List<BlockPos> vein = List.of(new BlockPos(3, 2, 3), new BlockPos(3, 3, 3), new BlockPos(3, 4, 3), new BlockPos(4, 4, 3), new BlockPos(4, 5, 4));
        for (BlockPos pos : vein) {
            h.setBlock(pos, Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(1, 2, 1), Blocks.OAK_LOG); // not connected: must survive
        // Game test servers default to creative, which drops nothing; vein mining is about survival play.
        player.setGameMode(GameType.SURVIVAL);
        VeinMining.setState(player, true, 0);
        h.assertTrue(player.gameMode.destroyBlock(h.absolutePos(vein.getFirst())), "break the first log");
        h.succeedWhen(() -> {
            for (BlockPos pos : vein) {
                h.assertBlockPresent(Blocks.AIR, pos);
            }
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, 2, 1));
            BlockPos origin = h.absolutePos(vein.getFirst());
            int near = logsIn(h, new AABB(origin).inflate(1.0));
            int everywhere = logsIn(h, new AABB(origin).inflate(8.0));
            h.assertTrue(everywhere == 5, "5 logs dropped, got " + everywhere);
            h.assertTrue(near == 5, "all drops gathered at the first block, " + near + " of 5 there");
            h.assertTrue(completed(player, "gt_vein"), "every vein-mined log counts for the break task");
            VeinMining.setState(player, false, 0);
            leave(h, player);
        });
    }

    private static int logsIn(GameTestHelper h, AABB box) {
        int n = 0;
        for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (item.getItem().is(Items.OAK_LOG)) {
                n += item.getItem().getCount();
            }
        }
        return n;
    }
}
