package dev.nightbeam.corerpg.quests.api;

import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.team.Team;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side events for scripts and addons. The player is the member whose action caused the event, or null when it
 * came from a command or another team member who is offline.
 */
public final class CoreQuestsEvents {
    private CoreQuestsEvents() {
    }

    public static final Event<QuestStarted> QUEST_STARTED = Event.create(ls -> (team, quest, player) -> ls.forEach(l -> l.onQuestStarted(team, quest, player)));
    public static final Event<QuestCompleted> QUEST_COMPLETED = Event.create(ls -> (team, quest, player) -> ls.forEach(l -> l.onQuestCompleted(team, quest, player)));
    public static final Event<TaskCompleted> TASK_COMPLETED = Event.create(ls -> (team, task, player) -> ls.forEach(l -> l.onTaskCompleted(team, task, player)));
    public static final Event<ChapterCompleted> CHAPTER_COMPLETED = Event.create(ls -> (team, chapter, player) -> ls.forEach(l -> l.onChapterCompleted(team, chapter, player)));
    public static final Event<RewardClaimed> REWARD_CLAIMED = Event.create(ls -> (player, reward) -> ls.forEach(l -> l.onRewardClaimed(player, reward)));
    public static final Event<CustomReward> CUSTOM_REWARD = Event.create(ls -> (player, reward) -> ls.forEach(l -> l.onCustomReward(player, reward)));
    public static final Event<StageChanged> STAGE_CHANGED = Event.create(ls -> (team, stage, added) -> ls.forEach(l -> l.onStageChanged(team, stage, added)));
    public static final Event<TeamChanged> PLAYER_TEAM_CHANGED = Event.create(ls -> (player, from, to) -> ls.forEach(l -> l.onTeamChanged(player, from, to)));

    @FunctionalInterface
    public interface QuestStarted {
        void onQuestStarted(Team team, Quest quest, @Nullable ServerPlayer player);
    }

    @FunctionalInterface
    public interface QuestCompleted {
        void onQuestCompleted(Team team, Quest quest, @Nullable ServerPlayer player);
    }

    @FunctionalInterface
    public interface TaskCompleted {
        void onTaskCompleted(Team team, Task task, @Nullable ServerPlayer player);
    }

    @FunctionalInterface
    public interface ChapterCompleted {
        void onChapterCompleted(Team team, Chapter chapter, @Nullable ServerPlayer player);
    }

    @FunctionalInterface
    public interface RewardClaimed {
        void onRewardClaimed(ServerPlayer player, Reward reward);
    }

    @FunctionalInterface
    public interface CustomReward {
        void onCustomReward(ServerPlayer player, dev.nightbeam.corerpg.quests.reward.types.CustomReward reward);
    }

    @FunctionalInterface
    public interface StageChanged {
        void onStageChanged(Team team, String stage, boolean added);
    }

    @FunctionalInterface
    public interface TeamChanged {
        void onTeamChanged(ServerPlayer player, @Nullable Team from, Team to);
    }
}
