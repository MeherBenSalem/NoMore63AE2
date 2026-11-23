package dev.nightbeam.corerpg.quests.team;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All teams of a world. Every player has a personal team; joining a party makes the party their active team until
 * they leave it. All rules live here so commands and the team screen behave the same.
 */
public final class TeamManager {
    /** Told about membership and team changes so progress can be re-synced. */
    public interface Listener {
        void onPlayerTeamChanged(UUID player, @Nullable Team from, Team to);

        void onTeamUpdated(Team team);

        void onStageChanged(Team team, String stage, boolean added);

        void onTeamDeleted(Team team);
    }

    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> partyOf = new HashMap<>();
    private final Set<UUID> teamChat = new HashSet<>();
    private Listener listener;
    private boolean dirty;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    private void changed(Team team) {
        dirty = true;
        if (listener != null) {
            listener.onTeamUpdated(team);
        }
    }

    // ---- Lookup ----

    public Collection<Team> all() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public @Nullable Team get(UUID id) {
        return teams.get(id);
    }

    /** Finds a team by full id, short id (first 8 hex digits) or exact name. */
    public @Nullable Team find(String query) {
        try {
            Team t = teams.get(UUID.fromString(query));
            if (t != null) {
                return t;
            }
        } catch (IllegalArgumentException ignored) {
        }
        String q = query.toLowerCase(Locale.ROOT);
        for (Team team : teams.values()) {
            if (team.shortId().equals(q)) {
                return team;
            }
        }
        for (Team team : teams.values()) {
            if (team.type() != TeamType.PLAYER && dev.nightbeam.corerpg.quests.util.TextUtil.plain(team.name()).equalsIgnoreCase(query)) {
                return team;
            }
        }
        for (Team team : teams.values()) {
            if (team.type() == TeamType.PLAYER && team.name().equalsIgnoreCase(query)) {
                return team;
            }
        }
        return null;
    }

    /** The player's personal team, created on first use. */
    public Team personalTeam(UUID player, String name) {
        Team team = teams.get(player);
        if (team == null) {
            team = new Team(player, TeamType.PLAYER, name, player);
            team.ranks.put(player, TeamRank.OWNER);
            team.playerNames.put(player, name);
            teams.put(player, team);
            dirty = true;
        } else if (!name.equals(team.playerNames.get(player)) && !name.isEmpty()) {
            team.playerNames.put(player, name);
            team.name = name;
            dirty = true;
        }
        return team;
    }

    /** The team whose progress the player plays with: their party, or their personal team. */
    public Team activeTeam(UUID player, String name) {
        UUID party = partyOf.get(player);
        if (party != null) {
            Team team = teams.get(party);
            if (team != null && team.isMember(player)) {
                if (!name.isEmpty()) {
                    team.playerNames.put(player, name);
                }
                return team;
            }
            partyOf.remove(player);
        }
        return personalTeam(player, name);
    }

    public @Nullable Team partyOf(UUID player) {
        UUID id = partyOf.get(player);
        return id == null ? null : teams.get(id);
    }

    public List<Team> invitesFor(UUID player) {
        List<Team> list = new ArrayList<>();
        for (Team team : teams.values()) {
            if (team.type() != TeamType.PLAYER && team.rank(player) == TeamRank.INVITED) {
                list.add(team);
            }
        }
        return list;
    }

    public boolean isTeamChat(UUID player) {
        return teamChat.contains(player);
    }

    public boolean toggleTeamChat(UUID player) {
        if (!teamChat.add(player)) {
            teamChat.remove(player);
            return false;
        }
        return true;
    }

    // ---- Party actions ----

    public Team createParty(UUID player, String playerName, String name) throws TeamException {
        if (!CoreConfig.SERVER.partiesEnabled.get()) {
            throw new TeamException("corerpg_quests.team.error.parties_disabled");
        }
        if (partyOf(player) != null) {
            throw new TeamException("corerpg_quests.team.error.already_in_party");
        }
        String clean = name.trim();
        if (clean.length() < 3 || clean.length() > 32) {
            throw new TeamException("corerpg_quests.team.error.name_length");
        }
        for (Team team : teams.values()) {
            if (team.type() != TeamType.PLAYER && team.name().equalsIgnoreCase(clean)) {
                throw new TeamException("corerpg_quests.team.error.name_taken", clean);
            }
        }
        Team from = activeTeam(player, playerName);
        Team party = new Team(UUID.randomUUID(), TeamType.PARTY, clean, player);
        party.ranks.put(player, TeamRank.OWNER);
        party.playerNames.put(player, playerName);
        teams.put(party.id(), party);
        partyOf.put(player, party.id());
        dirty = true;
        if (listener != null) {
            listener.onPlayerTeamChanged(player, from, party);
        }
        changed(party);
        return party;
    }

    public void invite(Team team, UUID actor, UUID target, String targetName) throws TeamException {
        requireParty(team);
        requireOfficer(team, actor);
        if (team.isMember(target)) {
            throw new TeamException("corerpg_quests.team.error.already_member", targetName);
        }
        if (partyOf(target) != null) {
            throw new TeamException("corerpg_quests.team.error.target_in_party", targetName);
        }
        team.ranks.put(target, TeamRank.INVITED);
        team.playerNames.put(target, targetName);
        changed(team);
    }

    public void cancelInvite(Team team, UUID actor, UUID target) throws TeamException {
        requireOfficer(team, actor);
        if (team.rank(target) == TeamRank.INVITED) {
            team.ranks.remove(target);
            changed(team);
        }
    }

    public void join(Team team, UUID player, String playerName) throws TeamException {
        requireParty(team);
        if (team.isMember(player)) {
            throw new TeamException("corerpg_quests.team.error.already_member", playerName);
        }
        if (partyOf(player) != null) {
            throw new TeamException("corerpg_quests.team.error.already_in_party");
        }
        if (team.rank(player) != TeamRank.INVITED && !team.freeToJoin) {
            throw new TeamException("corerpg_quests.team.error.not_invited");
        }
        int max = CoreConfig.SERVER.maxPartySize.get();
        if (max > 0 && team.members().size() >= max) {
            throw new TeamException("corerpg_quests.team.error.party_full", max);
        }
        Team from = activeTeam(player, playerName);
        team.ranks.put(player, TeamRank.MEMBER);
        team.playerNames.put(player, playerName);
        partyOf.put(player, team.id());
        dirty = true;
        if (listener != null) {
            listener.onPlayerTeamChanged(player, from, team);
        }
        changed(team);
    }

    public void decline(Team team, UUID player) {
        if (team.rank(player) == TeamRank.INVITED) {
            team.ranks.remove(player);
            changed(team);
        }
    }

    public void leave(UUID player, String playerName) throws TeamException {
        Team party = partyOf(player);
        if (party == null) {
            throw new TeamException("corerpg_quests.team.error.not_in_party");
        }
        if (party.owner().equals(player) && party.members().size() > 1) {
            throw new TeamException("corerpg_quests.team.error.owner_cant_leave");
        }
        removeMember(party, player, playerName);
        if (party.members().isEmpty()) {
            delete(party);
        }
    }

    public void kick(Team team, UUID actor, UUID target) throws TeamException {
        requireParty(team);
        requireOfficer(team, actor);
        if (!team.isMember(target)) {
            throw new TeamException("corerpg_quests.team.error.not_member", team.playerName(target));
        }
        if (team.owner().equals(target)) {
            throw new TeamException("corerpg_quests.team.error.cant_kick_owner");
        }
        if (team.rank(target).isOfficerOrAbove() && !team.owner().equals(actor)) {
            throw new TeamException("corerpg_quests.team.error.not_owner");
        }
        removeMember(team, target, team.playerName(target));
    }

    private void removeMember(Team team, UUID player, String playerName) {
        team.ranks.remove(player);
        partyOf.remove(player);
        teamChat.remove(player);
        dirty = true;
        Team personal = personalTeam(player, playerName);
        if (listener != null) {
            listener.onPlayerTeamChanged(player, team, personal);
        }
        changed(team);
    }

    public void promote(Team team, UUID actor, UUID target) throws TeamException {
        requireOwner(team, actor);
        if (team.rank(target) != TeamRank.MEMBER) {
            throw new TeamException("corerpg_quests.team.error.not_member", team.playerName(target));
        }
        team.ranks.put(target, TeamRank.OFFICER);
        changed(team);
    }

    public void demote(Team team, UUID actor, UUID target) throws TeamException {
        requireOwner(team, actor);
        if (team.rank(target) != TeamRank.OFFICER) {
            throw new TeamException("corerpg_quests.team.error.not_officer", team.playerName(target));
        }
        team.ranks.put(target, TeamRank.MEMBER);
        changed(team);
    }

    public void transferOwnership(Team team, UUID actor, UUID target) throws TeamException {
        requireParty(team);
        requireOwner(team, actor);
        if (!team.isMember(target) || target.equals(actor)) {
            throw new TeamException("corerpg_quests.team.error.not_member", team.playerName(target));
        }
        team.ranks.put(actor, TeamRank.OFFICER);
        team.ranks.put(target, TeamRank.OWNER);
        team.owner = target;
        changed(team);
    }

    public void disband(Team team, UUID actor) throws TeamException {
        requireParty(team);
        requireOwner(team, actor);
        for (UUID member : team.members()) {
            partyOf.remove(member);
            teamChat.remove(member);
            Team personal = personalTeam(member, team.playerName(member));
            if (listener != null) {
                listener.onPlayerTeamChanged(member, team, personal);
            }
        }
        delete(team);
    }

    private void delete(Team team) {
        teams.remove(team.id());
        dirty = true;
        if (listener != null) {
            listener.onTeamDeleted(team);
        }
    }

    public void setAlly(Team team, UUID actor, UUID target, String targetName, boolean ally) throws TeamException {
        requireOfficer(team, actor);
        if (team.isMember(target)) {
            throw new TeamException("corerpg_quests.team.error.already_member", targetName);
        }
        if (ally) {
            team.ranks.put(target, TeamRank.ALLY);
            team.playerNames.put(target, targetName);
        } else if (team.rank(target) == TeamRank.ALLY) {
            team.ranks.remove(target);
        }
        changed(team);
    }

    public void setName(Team team, UUID actor, String name) throws TeamException {
        requireParty(team);
        requireOfficer(team, actor);
        String clean = name.trim();
        if (clean.length() < 3 || clean.length() > 32) {
            throw new TeamException("corerpg_quests.team.error.name_length");
        }
        team.name = clean;
        changed(team);
    }

    public void setDescription(Team team, UUID actor, String description) throws TeamException {
        requireOfficer(team, actor);
        team.description = description.length() > 200 ? description.substring(0, 200) : description;
        changed(team);
    }

    public void setColor(Team team, UUID actor, int color) throws TeamException {
        requireOfficer(team, actor);
        team.color = color & 0xFFFFFF;
        changed(team);
    }

    public void setFreeToJoin(Team team, UUID actor, boolean free) throws TeamException {
        requireParty(team);
        requireOfficer(team, actor);
        team.freeToJoin = free;
        changed(team);
    }

    // ---- Server teams (admin) ----

    public Team createServerTeam(String name) throws TeamException {
        for (Team team : teams.values()) {
            if (team.type() == TeamType.SERVER && team.name().equalsIgnoreCase(name)) {
                throw new TeamException("corerpg_quests.team.error.name_taken", name);
            }
        }
        UUID id = UUID.randomUUID();
        Team team = new Team(id, TeamType.SERVER, name, id);
        teams.put(id, team);
        changed(team);
        return team;
    }

    public void deleteServerTeam(Team team) throws TeamException {
        if (team.type() != TeamType.SERVER) {
            throw new TeamException("corerpg_quests.team.error.not_server_team");
        }
        for (UUID member : team.members()) {
            partyOf.remove(member);
            if (listener != null) {
                listener.onPlayerTeamChanged(member, team, personalTeam(member, team.playerName(member)));
            }
        }
        delete(team);
    }

    /** Admin: puts a player into any party or server team, bypassing invites and size limits. */
    public void forceJoin(Team team, UUID player, String playerName) throws TeamException {
        if (team.type() == TeamType.PLAYER) {
            throw new TeamException("corerpg_quests.team.error.not_party");
        }
        Team from = activeTeam(player, playerName);
        Team oldParty = partyOf(player);
        if (oldParty != null && oldParty != team) {
            oldParty.ranks.remove(player);
            if (oldParty.members().isEmpty() && oldParty.type() == TeamType.PARTY) {
                delete(oldParty);
            } else {
                if (oldParty.owner().equals(player) && !oldParty.members().isEmpty()) {
                    UUID heir = oldParty.members().getFirst();
                    oldParty.owner = heir;
                    oldParty.ranks.put(heir, TeamRank.OWNER);
                }
                changed(oldParty);
            }
        }
        team.ranks.put(player, team.members().isEmpty() && team.type() == TeamType.PARTY ? TeamRank.OWNER : TeamRank.MEMBER);
        team.playerNames.put(player, playerName);
        partyOf.put(player, team.id());
        dirty = true;
        if (listener != null && from != team) {
            listener.onPlayerTeamChanged(player, from, team);
        }
        changed(team);
    }

    // ---- Stages ----

    public boolean addStage(Team team, String stage) {
        if (team.stages.add(stage)) {
            dirty = true;
            if (listener != null) {
                listener.onStageChanged(team, stage, true);
            }
            changed(team);
            return true;
        }
        return false;
    }

    public boolean removeStage(Team team, String stage) {
        if (team.stages.remove(stage)) {
            dirty = true;
            if (listener != null) {
                listener.onStageChanged(team, stage, false);
            }
            changed(team);
            return true;
        }
        return false;
    }

    // ---- Checks ----

    private static void requireParty(Team team) throws TeamException {
        if (team.type() != TeamType.PARTY) {
            throw new TeamException("corerpg_quests.team.error.not_party");
        }
    }

    private static void requireOfficer(Team team, UUID actor) throws TeamException {
        if (!team.rank(actor).isOfficerOrAbove()) {
            throw new TeamException("corerpg_quests.team.error.not_officer_self");
        }
    }

    private static void requireOwner(Team team, UUID actor) throws TeamException {
        if (!team.owner().equals(actor)) {
            throw new TeamException("corerpg_quests.team.error.not_owner");
        }
    }

    // ---- Persistence ----

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        teams.values().forEach(t -> array.add(t.toJson()));
        json.add("teams", array);
        JsonArray chat = new JsonArray();
        teamChat.forEach(u -> chat.add(u.toString()));
        json.add("team_chat", chat);
        return json;
    }

    public void fromJson(JsonObject json) {
        teams.clear();
        partyOf.clear();
        teamChat.clear();
        if (json.has("teams")) {
            json.getAsJsonArray("teams").forEach(e -> {
                Team team = Team.fromJson(e.getAsJsonObject());
                teams.put(team.id(), team);
            });
        }
        for (Team team : teams.values()) {
            if (team.type() != TeamType.PLAYER) {
                for (UUID member : team.members()) {
                    partyOf.put(member, team.id());
                }
            }
        }
        if (json.has("team_chat")) {
            json.getAsJsonArray("team_chat").forEach(e -> teamChat.add(UUID.fromString(e.getAsString())));
        }
        dirty = false;
    }
}
