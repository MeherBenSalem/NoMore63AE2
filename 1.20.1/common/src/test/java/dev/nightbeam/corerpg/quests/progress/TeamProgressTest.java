package dev.nightbeam.corerpg.quests.progress;

import dev.nightbeam.corerpg.quests.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamProgressTest {
    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Test
    void jsonRoundTrip() {
        TeamProgress p = new TeamProgress(TEAM);
        p.setTaskProgress("t1", 5);
        p.setTaskProgress("t2", 1L << 40);
        QuestRecord r = p.recordOrCreate("q1");
        r.started = 100;
        r.completed = 200;
        r.timesCompleted = 3;
        r.cooldownUntil = 300;
        p.recordOrCreate("q2").started = 7;
        p.setTeamClaimed("team_reward", true);
        p.setPlayerClaimed(P1, "r1", true);
        p.setPlayerClaimed(P2, "r2", true);
        p.setPinned(P1, "q2", true);
        p.setPinned(P1, "q1", true);
        p.setEmergencyUsed(P2, 999);
        p.setChapterCompleted("ch", 555);
        p.locked = true;
        p.rewardsBlocked = true;

        String text = JsonUtil.COMPACT.toJson(p.toJson());
        TeamProgress back = TeamProgress.fromJson(JsonUtil.parse(text).getAsJsonObject());
        assertEquals(p.toJson(), back.toJson());
        assertEquals(TEAM, back.teamId());
        assertEquals(1L << 40, back.taskProgress("t2"));
        assertEquals(3, back.record("q1").timesCompleted);
        assertTrue(back.isQuestCompleted("q1"));
        assertFalse(back.isQuestCompleted("q2"));
        assertTrue(back.isTeamClaimed("team_reward"));
        assertTrue(back.isPlayerClaimed(P1, "r1"));
        assertFalse(back.isPlayerClaimed(P1, "r2"));
        assertEquals(List.of("q2", "q1"), back.pinned(P1), "pin order is kept");
        assertEquals(999, back.emergencyUsed(P2));
        assertEquals(555, back.chapterCompleted("ch"));
        assertTrue(back.locked);
        assertTrue(back.rewardsBlocked);
    }

    @Test
    void dirtyTracking() {
        TeamProgress p = new TeamProgress(TEAM);
        assertFalse(p.isDirty());
        p.setTaskProgress("t", 0);
        assertFalse(p.isDirty(), "setting zero on an absent task is not a change");
        p.setTaskProgress("t", 2);
        assertTrue(p.isDirty());
        p.clearDirty();
        p.setTaskProgress("t", 2);
        assertFalse(p.isDirty(), "same value is not a change");
        p.setPlayerClaimed(P1, "r", false);
        assertFalse(p.isDirty());
        p.setTaskProgress("t", 0);
        assertTrue(p.isDirty());
        assertEquals(0, p.taskProgress("t"));
    }

    @Test
    void claimsAndReset() {
        TeamProgress p = new TeamProgress(TEAM);
        p.setTeamClaimed("r", true);
        p.setPlayerClaimed(P1, "r", true);
        p.setPlayerClaimed(P2, "r", true);
        p.clearClaims("r");
        assertFalse(p.isTeamClaimed("r"));
        assertFalse(p.isPlayerClaimed(P1, "r"));
        assertFalse(p.isPlayerClaimed(P2, "r"));
        p.setTaskProgress("t", 1);
        p.recordOrCreate("q").completed = 1;
        p.setPinned(P1, "q", true);
        p.resetAll();
        assertEquals(0, p.taskProgress("t"));
        assertNull(p.record("q"));
        assertEquals(List.of("q"), p.pinned(P1), "pins survive a progress reset");
    }
}
