package dev.nightbeam.corerpg.quests.team;

import dev.nightbeam.corerpg.quests.McTest;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamManagerTest extends McTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID CARA = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID DAN = UUID.fromString("00000000-0000-0000-0000-0000000000a4");

    private TeamManager teams;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        teams = new TeamManager();
        teams.setListener(new TeamManager.Listener() {
            @Override
            public void onPlayerTeamChanged(UUID player, Team from, Team to) {
                events.add("changed:" + player.toString().substring(34) + ":" + (from == null ? "-" : from.type().getSerializedName()) + "->" + to.type().getSerializedName());
            }

            @Override
            public void onTeamUpdated(Team team) {
                events.add("updated:" + team.name());
            }

            @Override
            public void onStageChanged(Team team, String stage, boolean added) {
                events.add((added ? "stage+:" : "stage-:") + stage);
            }

            @Override
            public void onTeamDeleted(Team team) {
                events.add("deleted:" + team.name());
            }
        });
    }

    @AfterEach
    void restoreConfig() {
        CoreConfig.SERVER.maxPartySize.set(0);
        CoreConfig.SERVER.partiesEnabled.set(true);
    }

    private static String key(TeamException e) {
        return e.getMessage();
    }

    private Team party() throws TeamException {
        return teams.createParty(OWNER, "Owner", "The Ashborn");
    }

    @Test
    void personalTeams() {
        Team t = teams.activeTeam(BOB, "Bob");
        assertEquals(TeamType.PLAYER, t.type());
        assertEquals(BOB, t.id());
        assertEquals(TeamRank.OWNER, t.rank(BOB));
        assertSame(t, teams.activeTeam(BOB, "Bob"));
        teams.activeTeam(BOB, "Bobby");
        assertEquals("Bobby", t.name(), "the personal team follows the player's name");
        assertTrue(teams.isDirty());
    }

    @Test
    void createParty() throws TeamException {
        Team p = party();
        assertEquals(TeamType.PARTY, p.type());
        assertSame(p, teams.activeTeam(OWNER, "Owner"));
        assertSame(p, teams.partyOf(OWNER));
        assertEquals(OWNER, p.owner());
        assertTrue(events.contains("changed:a1:player->party"));
        assertEquals("corerpg_quests.team.error.already_in_party", key(assertThrows(TeamException.class, this::party)));
        assertEquals("corerpg_quests.team.error.name_length", key(assertThrows(TeamException.class, () -> teams.createParty(BOB, "Bob", "ab"))));
        assertEquals("corerpg_quests.team.error.name_taken", key(assertThrows(TeamException.class, () -> teams.createParty(BOB, "Bob", "the ashborn"))));
        CoreConfig.SERVER.partiesEnabled.set(false);
        assertEquals("corerpg_quests.team.error.parties_disabled", key(assertThrows(TeamException.class, () -> teams.createParty(CARA, "Cara", "Other"))));
    }

    @Test
    void inviteAndJoin() throws TeamException {
        Team p = party();
        assertEquals("corerpg_quests.team.error.not_invited", key(assertThrows(TeamException.class, () -> teams.join(p, BOB, "Bob"))));
        assertEquals("corerpg_quests.team.error.not_officer_self", key(assertThrows(TeamException.class, () -> teams.invite(p, BOB, CARA, "Cara"))));
        teams.invite(p, OWNER, BOB, "Bob");
        assertEquals(TeamRank.INVITED, p.rank(BOB));
        assertEquals(List.of(p), teams.invitesFor(BOB));
        teams.join(p, BOB, "Bob");
        assertEquals(TeamRank.MEMBER, p.rank(BOB));
        assertSame(p, teams.activeTeam(BOB, "Bob"));
        assertTrue(teams.invitesFor(BOB).isEmpty());
        assertEquals("corerpg_quests.team.error.already_member", key(assertThrows(TeamException.class, () -> teams.invite(p, OWNER, BOB, "Bob"))));
        assertEquals("corerpg_quests.team.error.already_member", key(assertThrows(TeamException.class, () -> teams.join(p, BOB, "Bob"))));
        // Someone already in another party cannot be invited.
        Team other = teams.createParty(CARA, "Cara", "Other Party");
        assertEquals("corerpg_quests.team.error.target_in_party", key(assertThrows(TeamException.class, () -> teams.invite(p, OWNER, CARA, "Cara"))));
        assertNotNull(other);
    }

    @Test
    void declineAndCancelInvite() throws TeamException {
        Team p = party();
        teams.invite(p, OWNER, BOB, "Bob");
        teams.decline(p, BOB);
        assertEquals(TeamRank.NONE, p.rank(BOB));
        teams.invite(p, OWNER, BOB, "Bob");
        teams.cancelInvite(p, OWNER, BOB);
        assertEquals(TeamRank.NONE, p.rank(BOB));
    }

    @Test
    void freeToJoinAndSizeLimit() throws TeamException {
        Team p = party();
        teams.setFreeToJoin(p, OWNER, true);
        CoreConfig.SERVER.maxPartySize.set(2);
        teams.join(p, BOB, "Bob");
        assertEquals("corerpg_quests.team.error.party_full", key(assertThrows(TeamException.class, () -> teams.join(p, CARA, "Cara"))));
        CoreConfig.SERVER.maxPartySize.set(0);
        teams.join(p, CARA, "Cara");
        assertEquals(3, p.members().size());
    }

    @Test
    void leaving() throws TeamException {
        Team p = party();
        teams.setFreeToJoin(p, OWNER, true);
        teams.join(p, BOB, "Bob");
        assertEquals("corerpg_quests.team.error.owner_cant_leave", key(assertThrows(TeamException.class, () -> teams.leave(OWNER, "Owner"))));
        events.clear();
        teams.leave(BOB, "Bob");
        assertNull(teams.partyOf(BOB));
        assertEquals(TeamType.PLAYER, teams.activeTeam(BOB, "Bob").type());
        assertTrue(events.contains("changed:a2:party->player"));
        assertEquals("corerpg_quests.team.error.not_in_party", key(assertThrows(TeamException.class, () -> teams.leave(BOB, "Bob"))));
        // The last member leaving removes the party.
        teams.leave(OWNER, "Owner");
        assertNull(teams.get(p.id()));
        assertTrue(events.contains("deleted:The Ashborn"));
    }

    @Test
    void ranksKickPromoteDemoteTransfer() throws TeamException {
        Team p = party();
        teams.setFreeToJoin(p, OWNER, true);
        teams.join(p, BOB, "Bob");
        teams.join(p, CARA, "Cara");
        teams.join(p, DAN, "Dan");
        assertEquals("corerpg_quests.team.error.not_owner", key(assertThrows(TeamException.class, () -> teams.promote(p, BOB, CARA))));
        teams.promote(p, OWNER, BOB);
        assertEquals(TeamRank.OFFICER, p.rank(BOB));
        assertEquals("corerpg_quests.team.error.not_member", key(assertThrows(TeamException.class, () -> teams.promote(p, OWNER, BOB))));
        teams.kick(p, BOB, DAN);
        assertFalse(p.isMember(DAN), "officers can kick members");
        teams.promote(p, OWNER, CARA);
        assertEquals("corerpg_quests.team.error.not_owner", key(assertThrows(TeamException.class, () -> teams.kick(p, BOB, CARA))), "officers cannot kick officers");
        assertEquals("corerpg_quests.team.error.cant_kick_owner", key(assertThrows(TeamException.class, () -> teams.kick(p, BOB, OWNER))));
        teams.demote(p, OWNER, CARA);
        assertEquals(TeamRank.MEMBER, p.rank(CARA));
        assertEquals("corerpg_quests.team.error.not_officer", key(assertThrows(TeamException.class, () -> teams.demote(p, OWNER, CARA))));
        teams.transferOwnership(p, OWNER, CARA);
        assertEquals(CARA, p.owner());
        assertEquals(TeamRank.OWNER, p.rank(CARA));
        assertEquals(TeamRank.OFFICER, p.rank(OWNER));
        teams.leave(OWNER, "Owner");
        assertFalse(p.isMember(OWNER), "after the transfer the old owner may leave");
    }

    @Test
    void disband() throws TeamException {
        Team p = party();
        teams.setFreeToJoin(p, OWNER, true);
        teams.join(p, BOB, "Bob");
        assertEquals("corerpg_quests.team.error.not_owner", key(assertThrows(TeamException.class, () -> teams.disband(p, BOB))));
        events.clear();
        teams.disband(p, OWNER);
        assertNull(teams.get(p.id()));
        assertNull(teams.partyOf(OWNER));
        assertNull(teams.partyOf(BOB));
        assertTrue(events.contains("changed:a1:party->player"));
        assertTrue(events.contains("changed:a2:party->player"));
        assertTrue(events.contains("deleted:The Ashborn"));
    }

    @Test
    void alliesAndSettings() throws TeamException {
        Team p = party();
        teams.setAlly(p, OWNER, DAN, "Dan", true);
        assertEquals(TeamRank.ALLY, p.rank(DAN));
        assertFalse(p.isMember(DAN));
        assertTrue(p.rank(DAN).isAllyOrMember());
        teams.setAlly(p, OWNER, DAN, "Dan", false);
        assertEquals(TeamRank.NONE, p.rank(DAN));
        teams.setName(p, OWNER, "  Ember Guard ");
        assertEquals("Ember Guard", p.name());
        teams.setDescription(p, OWNER, "x".repeat(300));
        assertEquals(200, p.description().length());
        teams.setColor(p, OWNER, 0xFF123456);
        assertEquals(0x123456, p.color());
        assertEquals("corerpg_quests.team.error.not_officer_self", key(assertThrows(TeamException.class, () -> teams.setName(p, BOB, "Nope Nope"))));
    }

    @Test
    void stages() throws TeamException {
        Team p = party();
        assertTrue(teams.addStage(p, "gate_open"));
        assertFalse(teams.addStage(p, "gate_open"));
        assertTrue(p.hasStage("gate_open"));
        assertTrue(teams.removeStage(p, "gate_open"));
        assertFalse(teams.removeStage(p, "gate_open"));
        assertTrue(events.contains("stage+:gate_open"));
        assertTrue(events.contains("stage-:gate_open"));
    }

    @Test
    void serverTeams() throws TeamException {
        Team guild = teams.createServerTeam("Guild");
        assertEquals("corerpg_quests.team.error.name_taken", key(assertThrows(TeamException.class, () -> teams.createServerTeam("guild"))));
        teams.forceJoin(guild, BOB, "Bob");
        assertSame(guild, teams.activeTeam(BOB, "Bob"));
        Team p = party();
        teams.forceJoin(guild, OWNER, "Owner");
        assertNull(teams.get(p.id()), "the empty party left behind is removed");
        assertEquals("corerpg_quests.team.error.not_server_team", key(assertThrows(TeamException.class, () -> teams.deleteServerTeam(teams.activeTeam(CARA, "Cara")))));
        teams.deleteServerTeam(guild);
        assertEquals(TeamType.PLAYER, teams.activeTeam(BOB, "Bob").type());
    }

    @Test
    void forceJoinPassesOwnershipOn() throws TeamException {
        Team p = party();
        teams.setFreeToJoin(p, OWNER, true);
        teams.join(p, BOB, "Bob");
        Team guild = teams.createServerTeam("Guild");
        teams.forceJoin(guild, OWNER, "Owner");
        assertEquals(BOB, p.owner());
        assertEquals(TeamRank.OWNER, p.rank(BOB));
    }

    @Test
    void find() throws TeamException {
        Team p = party();
        assertSame(p, teams.find(p.id().toString()));
        assertSame(p, teams.find(p.shortId()));
        assertSame(p, teams.find("the ashborn"));
        Team bob = teams.activeTeam(BOB, "Bob");
        assertSame(bob, teams.find("bob"));
        assertNull(teams.find("nobody"));
    }

    @Test
    void teamChatToggle() {
        assertTrue(teams.toggleTeamChat(BOB));
        assertTrue(teams.isTeamChat(BOB));
        assertFalse(teams.toggleTeamChat(BOB));
        assertFalse(teams.isTeamChat(BOB));
    }

    @Test
    void jsonRoundTrip() throws TeamException {
        Team p = party();
        teams.setFreeToJoin(p, OWNER, true);
        teams.join(p, BOB, "Bob");
        teams.invite(p, OWNER, CARA, "Cara");
        teams.addStage(p, "s1");
        teams.setDescription(p, OWNER, "desc");
        teams.toggleTeamChat(BOB);
        teams.activeTeam(DAN, "Dan");
        String text = JsonUtil.COMPACT.toJson(teams.toJson());

        TeamManager back = new TeamManager();
        back.fromJson(JsonUtil.parse(text).getAsJsonObject());
        assertEquals(teams.toJson(), back.toJson());
        Team p2 = back.get(p.id());
        assertNotNull(p2);
        assertSame(p2, back.partyOf(BOB), "party membership is rebuilt");
        assertSame(p2, back.activeTeam(OWNER, "Owner"));
        assertEquals(TeamRank.INVITED, p2.rank(CARA));
        assertTrue(p2.hasStage("s1"));
        assertTrue(back.isTeamChat(BOB));
        assertEquals("Bob", p2.playerName(BOB));
        assertFalse(back.isDirty());
    }
}
