package dev.nightbeam.corerpg.quests.progress;

import dev.nightbeam.corerpg.quests.McTest;
import dev.nightbeam.corerpg.quests.TestBooks;
import dev.nightbeam.corerpg.quests.book.AutoclaimMode;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.DependencyMode;
import dev.nightbeam.corerpg.quests.book.ProgressionMode;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.Tristate;
import dev.nightbeam.corerpg.quests.reward.types.ItemReward;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.types.CheckmarkTask;
import dev.nightbeam.corerpg.quests.task.types.CustomTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestLogicTest extends McTest {
    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private QuestBook book;
    private Chapter ch;
    private TeamProgress progress;
    private long now;

    @BeforeEach
    void setUp() {
        book = new QuestBook();
        ch = TestBooks.chapter(book, "ch");
        progress = new TeamProgress(UUID.randomUUID());
        now = 1_000_000;
    }

    private QuestLogic logic() {
        book.rebuildIndex();
        return new QuestLogic(book, progress, () -> now);
    }

    private Quest quest(String id, String... deps) {
        Quest q = TestBooks.quest(ch, id, deps);
        TestBooks.check(q, id + "_t");
        return q;
    }

    private void complete(Quest q) {
        for (Task t : q.tasks) {
            progress.setTaskProgress(t.id(), t.maxProgress());
        }
        progress.recordOrCreate(q.id()).completed = now;
    }

    private void start(Quest q) {
        progress.recordOrCreate(q.id()).started = now;
    }

    @Test
    void allCompleted() {
        Quest a = quest("a");
        Quest b = quest("b");
        Quest c = quest("c", "a", "b");
        assertFalse(logic().areDependenciesMet(c));
        complete(a);
        assertFalse(logic().areDependenciesMet(c));
        assertEquals(QuestLogic.State.LOCKED, logic().state(c, P1));
        complete(b);
        assertTrue(logic().areDependenciesMet(c));
        assertEquals(QuestLogic.State.AVAILABLE, logic().state(c, P1));
    }

    @Test
    void oneCompleted() {
        Quest a = quest("a");
        quest("b");
        Quest c = quest("c", "a", "b");
        c.dependencyMode = DependencyMode.ONE_COMPLETED;
        assertFalse(logic().areDependenciesMet(c));
        complete(a);
        assertTrue(logic().areDependenciesMet(c));
    }

    @Test
    void allAndOneStarted() {
        Quest a = quest("a");
        Quest b = quest("b");
        Quest c = quest("c", "a", "b");
        c.dependencyMode = DependencyMode.ALL_STARTED;
        Quest d = quest("d", "a", "b");
        d.dependencyMode = DependencyMode.ONE_STARTED;
        assertFalse(logic().areDependenciesMet(c));
        assertFalse(logic().areDependenciesMet(d));
        start(a);
        assertFalse(logic().areDependenciesMet(c));
        assertTrue(logic().areDependenciesMet(d));
        complete(b);
        assertTrue(logic().areDependenciesMet(c), "completed counts as started");
    }

    @Test
    void startedFromTaskProgress() {
        Quest a = TestBooks.quest(ch, "a");
        CustomTask t = new CustomTask();
        t.setIdInternal("ct");
        t.value = 10;
        t.quest = a;
        a.tasks.add(t);
        assertFalse(logic().isStarted(a));
        progress.setTaskProgress("ct", 3);
        assertTrue(logic().isStarted(a));
        assertEquals(0.3, logic().fraction(a), 1e-9);
        assertEquals(QuestLogic.State.IN_PROGRESS, logic().state(a, P1));
    }

    @Test
    void minRequiredDependencies() {
        Quest a = quest("a");
        Quest b = quest("b");
        quest("c");
        Quest d = quest("d", "a", "b", "c");
        d.minRequiredDependencies = 2;
        complete(a);
        assertFalse(logic().areDependenciesMet(d));
        complete(b);
        assertTrue(logic().areDependenciesMet(d));
        d.minRequiredDependencies = 10;
        assertFalse(logic().areDependenciesMet(d), "capped at the number of dependencies (3)");
    }

    @Test
    void chapterDependenciesAndUnknownIds() {
        Chapter other = TestBooks.chapter(book, "other");
        Quest x = TestBooks.quest(other, "x");
        TestBooks.check(x, "x_t");
        Quest optional = TestBooks.quest(other, "opt");
        TestBooks.check(optional, "opt_t");
        optional.optional = true;
        Quest q = quest("q", "other");
        Quest unknown = quest("u", "does_not_exist");
        assertTrue(logic().areDependenciesMet(unknown), "unknown dependency ids are ignored");
        assertFalse(logic().areDependenciesMet(q));
        complete(x);
        assertTrue(logic().isChapterComplete(other), "optional quests do not hold a chapter back");
        assertTrue(logic().areDependenciesMet(q));
    }

    @Test
    void exclusiveBranches() {
        Quest root = quest("root");
        root.maxCompletableDependents = 1;
        Quest left = quest("left", "root");
        Quest right = quest("right", "root");
        complete(root);
        assertFalse(logic().isExcluded(left));
        complete(left);
        QuestLogic logic = logic();
        assertTrue(logic.isExcluded(right));
        assertFalse(logic.isExcluded(left), "completed quests are never excluded");
        assertFalse(logic.canStart(right));
        assertFalse(logic.canProgress(right.tasks.getFirst()));
        assertEquals(QuestLogic.State.EXCLUDED, logic.state(right, P1));
        assertFalse(logic.isVisible(right), "excluded quests are hidden by default");
        book.hideExcludedQuests = false;
        assertTrue(logic().isVisible(right));
    }

    @Test
    void cooldownUsesTheClock() {
        Quest q = quest("q");
        q.repeatable = true;
        progress.recordOrCreate("q").cooldownUntil = now + 5000;
        assertTrue(logic().isOnCooldown(q));
        assertEquals(5000, logic().cooldownRemainingMillis(q));
        assertEquals(QuestLogic.State.COOLDOWN, logic().state(q, P1));
        assertFalse(logic().canProgress(q.tasks.getFirst()));
        now += 5001;
        assertFalse(logic().isOnCooldown(q));
        assertTrue(logic().canProgress(q.tasks.getFirst()));
    }

    @Test
    void sequentialTasks() {
        Quest q = quest("q");
        CheckmarkTask second = TestBooks.check(q, "q_t2");
        assertTrue(logic().canProgress(second), "not sequential by default");
        q.requireSequentialTasks = Tristate.TRUE;
        assertFalse(logic().canProgress(second));
        assertTrue(logic().canProgress(q.tasks.getFirst()));
        progress.setTaskProgress("q_t", 1);
        assertTrue(logic().canProgress(second));
        q.requireSequentialTasks = Tristate.DEFAULT;
        ch.requireSequentialTasks = Tristate.TRUE;
        progress.setTaskProgress("q_t", 0);
        assertFalse(logic().canProgress(second), "chapter default applies");
    }

    @Test
    void linearAndFlexibleProgression() {
        quest("a");
        Quest b = quest("b", "a");
        Task t = b.tasks.getFirst();
        assertFalse(logic().canProgress(t), "linear: locked quests cannot progress");
        b.progressionMode = ProgressionMode.FLEXIBLE;
        assertTrue(logic().canProgress(t));
        b.progressionMode = ProgressionMode.DEFAULT;
        ch.progressionMode = ProgressionMode.FLEXIBLE;
        assertTrue(logic().canProgress(t), "chapter mode applies");
        ch.progressionMode = ProgressionMode.DEFAULT;
        book.progressionMode = ProgressionMode.FLEXIBLE;
        assertTrue(logic().canProgress(t), "book mode applies");
    }

    @Test
    void cannotProgressWhenLockedOrDone() {
        Quest q = quest("q");
        Task t = q.tasks.getFirst();
        progress.locked = true;
        assertFalse(logic().canProgress(t));
        progress.locked = false;
        progress.setTaskProgress(t.id(), 1);
        assertFalse(logic().canProgress(t), "a finished task");
        Quest q2 = quest("q2");
        complete(q2);
        progress.setTaskProgress(q2.tasks.getFirst().id(), 0);
        assertFalse(logic().canProgress(q2.tasks.getFirst()), "a completed quest");
    }

    @Test
    void optionalTasks() {
        Quest q = quest("q");
        CheckmarkTask extra = TestBooks.check(q, "extra");
        extra.optionalTask = true;
        assertFalse(logic().areTasksComplete(q));
        progress.setTaskProgress("q_t", 1);
        assertTrue(logic().areTasksComplete(q), "optional tasks are not required");
        Quest allOptional = quest("o");
        allOptional.tasks.getFirst().optionalTask = true;
        assertFalse(logic().areTasksComplete(allOptional), "if every task is optional, all are required");
        assertTrue(logic().areTasksComplete(TestBooks.quest(ch, "empty")), "a quest without tasks is ready");
    }

    @Test
    void visibilityRules() {
        Quest hidden = quest("hidden");
        hidden.invisible = true;
        assertFalse(logic().isVisible(hidden));
        complete(hidden);
        assertTrue(logic().isVisible(hidden), "completed quests always show");

        Quest twoTasks = quest("two");
        TestBooks.check(twoTasks, "two_b");
        twoTasks.invisibleUntilTasks = 1;
        assertFalse(logic().isVisible(twoTasks));
        progress.setTaskProgress("two_b", 1);
        assertTrue(logic().isVisible(twoTasks));

        Quest gate = quest("gate");
        Quest behind = quest("behind", "gate");
        behind.hideUntilDepsComplete = Tristate.TRUE;
        assertFalse(logic().isVisible(behind));
        complete(gate);
        assertTrue(logic().isVisible(behind));

        Quest secret = quest("secret");
        secret.invisible = true;
        Quest follows = quest("follows", "secret");
        follows.hideUntilDepsVisible = Tristate.TRUE;
        assertFalse(logic().isVisible(follows));
        ch.hideQuestUntilDepsVisible = true;
        follows.hideUntilDepsVisible = Tristate.DEFAULT;
        assertFalse(logic().isVisible(follows), "chapter default applies");
        complete(secret);
        assertTrue(logic().isVisible(follows));
    }

    @Test
    void visibilityCyclesTerminate() {
        Quest a = quest("a", "b");
        Quest b = quest("b", "a");
        a.hideUntilDepsVisible = Tristate.TRUE;
        b.hideUntilDepsVisible = Tristate.TRUE;
        QuestLogic logic = logic();
        logic.isVisible(a);
        logic.isVisible(b);
    }

    @Test
    void textAndChapterVisibility() {
        quest("a");
        Quest q = quest("q", "a");
        assertFalse(logic().isTextHidden(q));
        q.hideDetailsUntilStartable = Tristate.TRUE;
        assertTrue(logic().isTextHidden(q));
        q.hideDetailsUntilStartable = Tristate.DEFAULT;
        q.hideTextUntilComplete = Tristate.TRUE;
        assertTrue(logic().isTextHidden(q));
        complete(q);
        assertFalse(logic().isTextHidden(q));

        assertTrue(logic().isChapterVisible(ch));
        ch.alwaysInvisible = true;
        assertFalse(logic().isChapterVisible(ch));
        Chapter allHidden = TestBooks.chapter(book, "all_hidden");
        Quest h = TestBooks.quest(allHidden, "h");
        TestBooks.check(h, "h_t");
        h.invisible = true;
        assertFalse(logic().isChapterVisible(allHidden));
    }

    @Test
    void rewardClaimRules() {
        Quest q = quest("q");
        ItemReward personal = TestBooks.itemReward(q, "personal", 1);
        ItemReward shared = TestBooks.itemReward(q, "shared", 1);
        shared.teamReward = Tristate.TRUE;
        assertFalse(logic().canClaim(personal, P1), "not before completion");
        complete(q);
        QuestLogic logic = logic();
        assertTrue(logic.canClaim(personal, P1));
        assertEquals(QuestLogic.State.CLAIMABLE, logic.state(q, P1));
        progress.setPlayerClaimed(P1, "personal", true);
        progress.setTeamClaimed("shared", true);
        logic = logic();
        assertFalse(logic.canClaim(personal, P1));
        assertTrue(logic.canClaim(personal, P2), "personal rewards are per player");
        assertFalse(logic.canClaim(shared, P2), "team rewards are claimed once per team");
        assertTrue(logic.allRewardsClaimed(q, P1));
        assertFalse(logic.allRewardsClaimed(q, P2));
        assertEquals(QuestLogic.State.COMPLETED, logic.state(q, P1));
    }

    @Test
    void bookDefaultTeamRewards() {
        Quest q = quest("q");
        ItemReward r = TestBooks.itemReward(q, "r", 1);
        logic();
        assertFalse(r.isTeamReward());
        book.defaultTeamRewards = true;
        assertTrue(r.isTeamReward());
        r.teamReward = Tristate.FALSE;
        assertFalse(r.isTeamReward());
    }

    @Test
    void rewardBlocking() {
        Quest q = quest("q");
        ItemReward r = TestBooks.itemReward(q, "r", 1);
        complete(q);
        progress.rewardsBlocked = true;
        assertFalse(logic().canClaim(r, P1));
        r.ignoreRewardBlocking = true;
        assertTrue(logic().canClaim(r, P1));
        r.ignoreRewardBlocking = false;
        q.ignoreRewardBlocking = true;
        assertTrue(logic().canClaim(r, P1));
    }

    @Test
    void invisibleAutoclaimRewardsDoNotNag() {
        Quest q = quest("q");
        ItemReward r = TestBooks.itemReward(q, "r", 1);
        r.autoclaim = AutoclaimMode.INVISIBLE;
        complete(q);
        assertFalse(logic().hasUnclaimedRewards(q, P1));
        r.autoclaim = AutoclaimMode.DEFAULT;
        assertTrue(logic().hasUnclaimedRewards(q, P1));
        assertTrue(logic().hasUnclaimedRewards(ch, P1));
        book.suppressAllAutoclaim = true;
        r.autoclaim = AutoclaimMode.ENABLED;
        assertEquals(AutoclaimMode.DISABLED, r.effectiveAutoclaim(), "suppress_all_autoclaim wins");
    }

    @Test
    void chapterAndBookFractions() {
        Quest a = quest("a");
        quest("b");
        Quest opt = quest("opt");
        opt.optional = true;
        assertEquals(0, logic().chapterFraction(ch));
        complete(a);
        assertEquals(0.5, logic().chapterFraction(ch), 1e-9);
        assertEquals(0.5, logic().bookFraction(), 1e-9);
        assertFalse(logic().isChapterComplete(ch));
        complete(ch.quests.get(1));
        assertTrue(logic().isChapterComplete(ch));
        assertEquals(1.0, logic().bookFraction(), 1e-9);
    }
}
