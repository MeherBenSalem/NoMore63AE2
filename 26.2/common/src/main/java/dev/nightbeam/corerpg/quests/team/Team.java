package dev.nightbeam.corerpg.quests.team;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** A team. Quest progress and stages belong to teams, so party members share them. */
public final class Team {
    private final UUID id;
    private final TeamType type;
    String name;
    String description = "";
    int color;
    boolean freeToJoin = false;
    UUID owner;
    final Map<UUID, TeamRank> ranks = new LinkedHashMap<>();
    final Map<UUID, String> playerNames = new LinkedHashMap<>();
    final Set<String> stages = new TreeSet<>();
    long createdAt;

    Team(UUID id, TeamType type, String name, UUID owner) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.owner = owner;
        this.color = colorFor(id);
        this.createdAt = System.currentTimeMillis();
    }

    /** A pleasant, stable colour per team id. */
    static int colorFor(UUID id) {
        float hue = (id.hashCode() & 0xFFFF) / 65535f;
        return net.minecraft.util.Mth.hsvToRgb(hue, 0.55f, 0.95f) & 0xFFFFFF;
    }

    public UUID id() {
        return id;
    }

    public TeamType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int color() {
        return color;
    }

    public boolean freeToJoin() {
        return freeToJoin;
    }

    public UUID owner() {
        return owner;
    }

    public long createdAt() {
        return createdAt;
    }

    public TeamRank rank(UUID player) {
        return ranks.getOrDefault(player, TeamRank.NONE);
    }

    public boolean isMember(UUID player) {
        return rank(player).isMember();
    }

    public List<UUID> members() {
        List<UUID> list = new ArrayList<>();
        ranks.forEach((uuid, rank) -> {
            if (rank.isMember()) {
                list.add(uuid);
            }
        });
        return list;
    }

    public List<UUID> withRank(TeamRank wanted) {
        List<UUID> list = new ArrayList<>();
        ranks.forEach((uuid, rank) -> {
            if (rank == wanted) {
                list.add(uuid);
            }
        });
        return list;
    }

    public Map<UUID, TeamRank> ranks() {
        return Collections.unmodifiableMap(ranks);
    }

    public Set<String> stages() {
        return Collections.unmodifiableSet(stages);
    }

    public boolean hasStage(String stage) {
        return stages.contains(stage);
    }

    public String playerName(UUID uuid) {
        return playerNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    public List<ServerPlayer> onlineMembers(MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID uuid : members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    public MutableComponent displayName() {
        return TextUtil.format(name).copy().withStyle(s -> s.withColor(TextColor.fromRgb(color)));
    }

    /** Short id for commands: the first 8 hex digits. */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public Component describe() {
        return displayName().append(Component.literal(" [" + shortId() + "]").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("type", type.getSerializedName());
        json.addProperty("name", name);
        if (!description.isEmpty()) {
            json.addProperty("description", description);
        }
        json.addProperty("color", color);
        json.addProperty("free_to_join", freeToJoin);
        json.addProperty("owner", owner.toString());
        json.addProperty("created", createdAt);
        JsonObject rankJson = new JsonObject();
        ranks.forEach((uuid, rank) -> rankJson.addProperty(uuid.toString(), rank.getSerializedName()));
        json.add("ranks", rankJson);
        JsonObject names = new JsonObject();
        playerNames.forEach((uuid, n) -> names.addProperty(uuid.toString(), n));
        json.add("names", names);
        JsonArray stageArray = new JsonArray();
        stages.forEach(stageArray::add);
        json.add("stages", stageArray);
        return json;
    }

    static Team fromJson(JsonObject json) {
        UUID id = UUID.fromString(json.get("id").getAsString());
        Team team = new Team(id, TeamType.byName(JsonUtil.getString(json, "type", "player")),
                JsonUtil.getString(json, "name", "Team"), UUID.fromString(JsonUtil.getString(json, "owner", id.toString())));
        team.description = JsonUtil.getString(json, "description", "");
        team.color = JsonUtil.getInt(json, "color", colorFor(id)) & 0xFFFFFF;
        team.freeToJoin = JsonUtil.getBool(json, "free_to_join", false);
        team.createdAt = JsonUtil.getLong(json, "created", System.currentTimeMillis());
        JsonUtil.getObject(json, "ranks").entrySet().forEach(e -> team.ranks.put(UUID.fromString(e.getKey()), TeamRank.byName(e.getValue().getAsString())));
        JsonUtil.getObject(json, "names").entrySet().forEach(e -> team.playerNames.put(UUID.fromString(e.getKey()), e.getValue().getAsString()));
        if (json.has("stages")) {
            json.getAsJsonArray("stages").forEach(e -> team.stages.add(e.getAsString()));
        }
        return team;
    }

    /** What clients see of a team. */
    public JsonObject toClientJson() {
        return toJson();
    }

    public static Team fromClientJson(JsonObject json) {
        return fromJson(json);
    }
}
