package dev.nightbeam.corerpg.quests.task;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/** What a task sees while it is being progressed: the player who acted and their team's progress on this task. */
public interface TaskContext {
    ServerPlayer player();

    long progress();

    /** Sets the team's progress on this task (clamped to 0..max). */
    void setProgress(long value);

    default void addProgress(long amount) {
        if (amount > 0) {
            setProgress(progress() + amount);
        }
    }

    /** The team's stages. */
    Set<String> stages();

    /** Whether the team (the player's team) has completed the given quest. */
    boolean isQuestCompleted(String questId);
}
