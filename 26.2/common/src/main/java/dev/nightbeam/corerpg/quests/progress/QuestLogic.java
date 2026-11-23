package dev.nightbeam.corerpg.quests.progress;

import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.DependencyMode;
import dev.nightbeam.corerpg.quests.book.ProgressionMode;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.task.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Answers every "what state is this quest in" question for one team. Shared by the server (to decide what may
 * progress) and the client (to draw the book), so both always agree.
 */
public final class QuestLogic {
    public enum State {
        /** Dependencies are not met. */
        LOCKED,
        /** Another branch was chosen (max completable dependents reached). */
        EXCLUDED,
        /** Repeatable quest waiting for its cooldown. */
        COOLDOWN,
        /** Unlocked, no progress yet. */
        AVAILABLE,
        IN_PROGRESS,
        /** Done, with rewards the player can still claim. */
        CLAIMABLE,
        COMPLETED
    }

    private final QuestBook book;
    private final TeamProgress progress;
    private final LongSupplier clock;
    private final Map<String, Boolean> visibleCache = new HashMap<>();

    public QuestLogic(QuestBook book, TeamProgress progress) {
        this(book, progress, System::currentTimeMillis);
    }

    public QuestLogic(QuestBook book, TeamProgress progress, LongSupplier clock) {
        this.book = book;
        this.progress = progress;
        this.clock = clock;
    }

    public QuestBook book() {
        return book;
    }

    public TeamProgress progress() {
        return progress;
    }

    // ---- Tasks ----

    public long progress(Task task) {
        return Math.min(progress.taskProgress(task.id()), task.maxProgress());
    }

    public boolean isComplete(Task task) {
        return progress.taskProgress(task.id()) >= task.maxProgress();
    }

    public double fraction(Task task) {
        long max = task.maxProgress();
        return max <= 0 ? 1 : Math.min(1.0, progress(task) / (double) max);
    }

    /** Whether the task may take progress right now. */
    public boolean canProgress(Task task) {
        Quest quest = task.quest;
        if (quest == null || progress.locked || isComplete(task) || isCompleted(quest)) {
            return false;
        }
        if (isExcluded(quest) || isOnCooldown(quest)) {
            return false;
        }
        if (quest.effectiveProgressionMode() != ProgressionMode.FLEXIBLE && !areDependenciesMet(quest)) {
            return false;
        }
        if (quest.effectiveRequireSequentialTasks()) {
            for (Task t : quest.tasks) {
                if (t == task) {
                    break;
                }
                if (!isComplete(t)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- Quests ----

    public boolean isCompleted(Quest quest) {
        return progress.isQuestCompleted(quest.id());
    }

    public boolean isStarted(Quest quest) {
        if (isCompleted(quest)) {
            return true;
        }
        QuestRecord r = progress.record(quest.id());
        if (r != null && r.started > 0) {
            return true;
        }
        for (Task task : quest.tasks) {
            if (progress.taskProgress(task.id()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** All required tasks are done (the quest may still wait for dependencies in flexible mode). */
    public boolean areTasksComplete(Quest quest) {
        if (quest.tasks.isEmpty()) {
            return true;
        }
        for (Task task : quest.requiredTasks()) {
            if (!isComplete(task)) {
                return false;
            }
        }
        return true;
    }

    public int completedTaskCount(Quest quest) {
        int n = 0;
        for (Task task : quest.tasks) {
            if (isComplete(task)) {
                n++;
            }
        }
        return n;
    }

    public double fraction(Quest quest) {
        if (isCompleted(quest)) {
            return 1;
        }
        List<Task> required = quest.requiredTasks();
        if (required.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Task task : required) {
            sum += fraction(task);
        }
        return sum / required.size();
    }

    /** Dependencies may be quests or whole chapters; unknown ids are ignored. */
    public boolean areDependenciesMet(Quest quest) {
        if (quest.dependencies.isEmpty()) {
            return true;
        }
        int total = 0;
        int completed = 0;
        int started = 0;
        for (String depId : quest.dependencies) {
            QuestObject dep = book.get(depId);
            if (dep instanceof Quest q) {
                total++;
                if (isCompleted(q)) {
                    completed++;
                    started++;
                } else if (isStarted(q)) {
                    started++;
                }
            } else if (dep instanceof Chapter c) {
                total++;
                if (isChapterComplete(c)) {
                    completed++;
                    started++;
                } else if (chapterFraction(c) > 0) {
                    started++;
                }
            }
        }
        if (total == 0) {
            return true;
        }
        if (quest.minRequiredDependencies > 0) {
            return completed >= Math.min(quest.minRequiredDependencies, total);
        }
        DependencyMode mode = quest.dependencyMode;
        return switch (mode) {
            case ALL_COMPLETED -> completed == total;
            case ONE_COMPLETED -> completed >= 1;
            case ALL_STARTED -> started == total;
            case ONE_STARTED -> started >= 1;
        };
    }

    /**
     * A quest is excluded when one of its dependencies only allows a limited number of its dependents to be completed
     * (branching choices) and that many other dependents are already done.
     */
    public boolean isExcluded(Quest quest) {
        if (isCompleted(quest)) {
            return false;
        }
        for (String depId : quest.dependencies) {
            Quest dep = book.quest(depId);
            if (dep == null || dep.maxCompletableDependents <= 0) {
                continue;
            }
            int done = 0;
            for (Quest sibling : book.dependents(dep.id())) {
                if (sibling != quest && isCompleted(sibling)) {
                    done++;
                }
            }
            if (done >= dep.maxCompletableDependents) {
                return true;
            }
        }
        return false;
    }

    public boolean isOnCooldown(Quest quest) {
        QuestRecord r = progress.record(quest.id());
        return r != null && !r.isCompleted() && r.cooldownUntil > clock.getAsLong();
    }

    public long cooldownRemainingMillis(Quest quest) {
        QuestRecord r = progress.record(quest.id());
        return r == null ? 0 : Math.max(0, r.cooldownUntil - clock.getAsLong());
    }

    public boolean canStart(Quest quest) {
        return !isCompleted(quest) && !isExcluded(quest) && !isOnCooldown(quest) && areDependenciesMet(quest);
    }

    public State state(Quest quest, UUID player) {
        if (isCompleted(quest)) {
            return hasUnclaimedRewards(quest, player) ? State.CLAIMABLE : State.COMPLETED;
        }
        if (isExcluded(quest)) {
            return State.EXCLUDED;
        }
        if (isOnCooldown(quest)) {
            return State.COOLDOWN;
        }
        if (!areDependenciesMet(quest)) {
            return State.LOCKED;
        }
        return isStarted(quest) ? State.IN_PROGRESS : State.AVAILABLE;
    }

    // ---- Visibility ----

    /** Whether the quest is shown to players (editors see everything). */
    public boolean isVisible(Quest quest) {
        Boolean cached = visibleCache.get(quest.id());
        if (cached != null) {
            return cached;
        }
        visibleCache.put(quest.id(), true);
        boolean visible = computeVisible(quest);
        visibleCache.put(quest.id(), visible);
        return visible;
    }

    private boolean computeVisible(Quest quest) {
        if (isCompleted(quest)) {
            return true;
        }
        if (quest.invisible) {
            return false;
        }
        if (quest.invisibleUntilTasks > 0 && completedTaskCount(quest) < quest.invisibleUntilTasks) {
            return false;
        }
        if (book.hideExcludedQuests && isExcluded(quest)) {
            return false;
        }
        if (quest.effectiveHideUntilDepsComplete() && !areDependenciesMet(quest)) {
            return false;
        }
        if (quest.effectiveHideUntilDepsVisible()) {
            for (String depId : quest.dependencies) {
                Quest dep = book.quest(depId);
                if (dep != null && !isVisible(dep)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Hidden description and tasks until the quest is complete (or startable, per settings). */
    public boolean isTextHidden(Quest quest) {
        if (isCompleted(quest)) {
            return false;
        }
        if (quest.effectiveHideTextUntilComplete()) {
            return true;
        }
        return quest.effectiveHideDetailsUntilStartable() && !areDependenciesMet(quest);
    }

    public boolean isChapterVisible(Chapter chapter) {
        if (chapter.alwaysInvisible) {
            return false;
        }
        for (Quest quest : chapter.quests) {
            if (isVisible(quest)) {
                return true;
            }
        }
        return !chapter.links.isEmpty() && chapter.quests.isEmpty();
    }

    // ---- Chapters and book ----

    public boolean isChapterComplete(Chapter chapter) {
        boolean any = false;
        for (Quest quest : chapter.quests) {
            if (quest.optional) {
                continue;
            }
            any = true;
            if (!isCompleted(quest)) {
                return false;
            }
        }
        return any;
    }

    public double chapterFraction(Chapter chapter) {
        int total = 0;
        double sum = 0;
        for (Quest quest : chapter.quests) {
            if (quest.optional) {
                continue;
            }
            total++;
            sum += isCompleted(quest) ? 1 : fraction(quest) * 0.999;
        }
        return total == 0 ? 0 : sum / total;
    }

    public double bookFraction() {
        int total = 0;
        double sum = 0;
        for (Chapter chapter : book.chapters) {
            for (Quest quest : chapter.quests) {
                if (!quest.optional) {
                    total++;
                    sum += isCompleted(quest) ? 1 : 0;
                }
            }
        }
        return total == 0 ? 0 : sum / total;
    }

    // ---- Rewards ----

    public boolean isClaimed(Reward reward, UUID player) {
        return reward.isTeamReward() ? progress.isTeamClaimed(reward.id()) : progress.isPlayerClaimed(player, reward.id());
    }

    public boolean canClaim(Reward reward, UUID player) {
        Quest quest = reward.quest;
        if (quest == null || !isCompleted(quest) || isClaimed(reward, player)) {
            return false;
        }
        return !progress.rewardsBlocked || reward.ignoreRewardBlocking || quest.ignoreRewardBlocking;
    }

    public boolean hasUnclaimedRewards(Quest quest, UUID player) {
        if (!isCompleted(quest)) {
            return false;
        }
        for (Reward reward : quest.rewards) {
            if (reward.effectiveAutoclaim() != dev.nightbeam.corerpg.quests.book.AutoclaimMode.INVISIBLE && canClaim(reward, player)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasUnclaimedRewards(Chapter chapter, UUID player) {
        for (Quest quest : chapter.quests) {
            if (hasUnclaimedRewards(quest, player)) {
                return true;
            }
        }
        return false;
    }

    /** All rewards of the quest have been claimed by this player (team rewards by anyone). */
    public boolean allRewardsClaimed(Quest quest, UUID player) {
        for (Reward reward : quest.rewards) {
            if (!isClaimed(reward, player)) {
                return false;
            }
        }
        return true;
    }
}
