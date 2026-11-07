package dev.nightbeam.corerpg.quests.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.util.JsonUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a team has done in the quest book: task progress, quest records, claimed rewards (per player, and per
 * team for team rewards) and each member's pinned quests.
 */
public final class TeamProgress {
    private final UUID teamId;
    private final Map<String, Long> tasks = new HashMap<>();
    private final Map<String, QuestRecord> quests = new HashMap<>();
    private final Set<String> teamClaims = new HashSet<>();
    private final Map<UUID, Set<String>> playerClaims = new HashMap<>();
    private final Map<UUID, LinkedHashSet<String>> pinned = new HashMap<>();
    private final Map<UUID, Long> emergencyUsed = new HashMap<>();
    private final Map<String, Long> chapterCompleted = new LinkedHashMap<>();
    public boolean locked;
    public boolean rewardsBlocked;
    private boolean dirty;

    public TeamProgress(UUID teamId) {
        this.teamId = teamId;
    }

    public UUID teamId() {
        return teamId;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    // ---- Tasks ----

    public long taskProgress(String taskId) {
        return tasks.getOrDefault(taskId, 0L);
    }

    public void setTaskProgress(String taskId, long value) {
        if (value <= 0) {
            if (tasks.remove(taskId) != null) {
                dirty = true;
            }
        } else if (!Long.valueOf(value).equals(tasks.put(taskId, value))) {
            dirty = true;
        }
    }

    // ---- Quests ----

    public QuestRecord record(String questId) {
        return quests.get(questId);
    }

    public QuestRecord recordOrCreate(String questId) {
        dirty = true;
        return quests.computeIfAbsent(questId, k -> new QuestRecord());
    }

    public void removeRecord(String questId) {
        if (quests.remove(questId) != null) {
            dirty = true;
        }
    }

    public boolean isQuestCompleted(String questId) {
        QuestRecord r = quests.get(questId);
        return r != null && r.isCompleted();
    }

    public long chapterCompleted(String chapterId) {
        return chapterCompleted.getOrDefault(chapterId, 0L);
    }

    public void setChapterCompleted(String chapterId, long time) {
        if (time <= 0) {
            chapterCompleted.remove(chapterId);
        } else {
            chapterCompleted.put(chapterId, time);
        }
        dirty = true;
    }

    // ---- Rewards ----

    public boolean isTeamClaimed(String rewardId) {
        return teamClaims.contains(rewardId);
    }

    public boolean isPlayerClaimed(UUID player, String rewardId) {
        Set<String> set = playerClaims.get(player);
        return set != null && set.contains(rewardId);
    }

    public void setTeamClaimed(String rewardId, boolean claimed) {
        if (claimed ? teamClaims.add(rewardId) : teamClaims.remove(rewardId)) {
            dirty = true;
        }
    }

    public void setPlayerClaimed(UUID player, String rewardId, boolean claimed) {
        Set<String> set = playerClaims.computeIfAbsent(player, k -> new HashSet<>());
        if (claimed ? set.add(rewardId) : set.remove(rewardId)) {
            dirty = true;
        }
    }

    /** Forgets every claim of a reward (used when a quest is reset or repeats). */
    public void clearClaims(String rewardId) {
        if (teamClaims.remove(rewardId)) {
            dirty = true;
        }
        for (Set<String> set : playerClaims.values()) {
            if (set.remove(rewardId)) {
                dirty = true;
            }
        }
    }

    // ---- Pins ----

    public List<String> pinned(UUID player) {
        Set<String> set = pinned.get(player);
        return set == null ? List.of() : List.copyOf(set);
    }

    public boolean isPinned(UUID player, String questId) {
        Set<String> set = pinned.get(player);
        return set != null && set.contains(questId);
    }

    public void setPinned(UUID player, String questId, boolean pin) {
        LinkedHashSet<String> set = pinned.computeIfAbsent(player, k -> new LinkedHashSet<>());
        if (pin ? set.add(questId) : set.remove(questId)) {
            dirty = true;
        }
    }

    public long emergencyUsed(UUID player) {
        return emergencyUsed.getOrDefault(player, 0L);
    }

    public void setEmergencyUsed(UUID player, long time) {
        emergencyUsed.put(player, time);
        dirty = true;
    }

    /** Clears everything (admin reset). */
    public void resetAll() {
        tasks.clear();
        quests.clear();
        teamClaims.clear();
        playerClaims.clear();
        chapterCompleted.clear();
        dirty = true;
    }

    public Set<String> taskIds() {
        return Collections.unmodifiableSet(tasks.keySet());
    }

    public Set<String> questIds() {
        return Collections.unmodifiableSet(quests.keySet());
    }

    // ---- JSON ----

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("team", teamId.toString());
        JsonObject taskJson = new JsonObject();
        tasks.forEach(taskJson::addProperty);
        json.add("tasks", taskJson);
        JsonObject questJson = new JsonObject();
        quests.forEach((id, r) -> questJson.add(id, r.toJson()));
        json.add("quests", questJson);
        JsonArray team = new JsonArray();
        teamClaims.forEach(team::add);
        json.add("team_claims", team);
        JsonObject players = new JsonObject();
        playerClaims.forEach((uuid, set) -> {
            if (!set.isEmpty()) {
                JsonArray array = new JsonArray();
                set.forEach(array::add);
                players.add(uuid.toString(), array);
            }
        });
        json.add("player_claims", players);
        JsonObject pins = new JsonObject();
        pinned.forEach((uuid, set) -> {
            if (!set.isEmpty()) {
                JsonArray array = new JsonArray();
                set.forEach(array::add);
                pins.add(uuid.toString(), array);
            }
        });
        json.add("pinned", pins);
        JsonObject emergency = new JsonObject();
        emergencyUsed.forEach((uuid, t) -> emergency.addProperty(uuid.toString(), t));
        json.add("emergency", emergency);
        JsonObject chapters = new JsonObject();
        chapterCompleted.forEach(chapters::addProperty);
        json.add("chapters", chapters);
        json.addProperty("locked", locked);
        json.addProperty("rewards_blocked", rewardsBlocked);
        return json;
    }

    public static TeamProgress fromJson(JsonObject json) {
        TeamProgress p = new TeamProgress(UUID.fromString(json.get("team").getAsString()));
        JsonUtil.getObject(json, "tasks").entrySet().forEach(e -> p.tasks.put(e.getKey(), e.getValue().getAsLong()));
        JsonUtil.getObject(json, "quests").entrySet().forEach(e -> p.quests.put(e.getKey(), QuestRecord.fromJson(e.getValue().getAsJsonObject())));
        if (json.has("team_claims")) {
            json.getAsJsonArray("team_claims").forEach(e -> p.teamClaims.add(e.getAsString()));
        }
        JsonUtil.getObject(json, "player_claims").entrySet().forEach(e -> {
            Set<String> set = new HashSet<>();
            e.getValue().getAsJsonArray().forEach(x -> set.add(x.getAsString()));
            p.playerClaims.put(UUID.fromString(e.getKey()), set);
        });
        JsonUtil.getObject(json, "pinned").entrySet().forEach(e -> {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            e.getValue().getAsJsonArray().forEach(x -> set.add(x.getAsString()));
            p.pinned.put(UUID.fromString(e.getKey()), set);
        });
        JsonUtil.getObject(json, "emergency").entrySet().forEach(e -> p.emergencyUsed.put(UUID.fromString(e.getKey()), e.getValue().getAsLong()));
        JsonUtil.getObject(json, "chapters").entrySet().forEach(e -> p.chapterCompleted.put(e.getKey(), e.getValue().getAsLong()));
        p.locked = JsonUtil.getBool(json, "locked", false);
        p.rewardsBlocked = JsonUtil.getBool(json, "rewards_blocked", false);
        return p;
    }

    /** Ids of every reward claimed by anyone in this progress (for cleanup on reset). */
    public List<String> allClaimedRewardIds() {
        List<String> list = new ArrayList<>(teamClaims);
        playerClaims.values().forEach(list::addAll);
        return list;
    }
}
