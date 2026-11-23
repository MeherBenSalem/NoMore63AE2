package dev.nightbeam.corerpg.quests.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.progress.QuestLogic;
import dev.nightbeam.corerpg.quests.progress.TeamProgress;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.team.Team;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The client's synced copy of the book, its team and progress, and editor state. */
public final class ClientQuests {
    public record Invite(UUID id, String name, int color) {
    }

    public record OnlinePlayer(UUID id, String name) {
    }

    public record OpenParty(UUID id, String name, int color, int members) {
    }

    private static @Nullable QuestBook book;
    private static @Nullable TeamProgress progress;
    private static @Nullable Team team;
    private static UUID self = new UUID(0, 0);
    private static boolean editing;
    private static boolean canEdit;
    private static boolean teamChat;
    private static List<Invite> invites = List.of();
    private static List<OnlinePlayer> online = List.of();
    private static List<OpenParty> openParties = List.of();
    private static JsonObject editorData = new JsonObject();
    private static int version;
    private static String lastChapter = "";
    private static @Nullable String lastCreated;

    private ClientQuests() {
    }

    public static void reset() {
        book = null;
        progress = null;
        team = null;
        editing = false;
        canEdit = false;
        invites = List.of();
        online = List.of();
        openParties = List.of();
        editorData = new JsonObject();
        version++;
    }

    public static JsonCtx ctx() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            return new JsonCtx(mc.getConnection().registryAccess());
        }
        return new JsonCtx(mc.level != null ? mc.level.registryAccess() : null);
    }

    public static boolean ready() {
        return book != null && progress != null && team != null;
    }

    public static @Nullable QuestBook book() {
        return book;
    }

    public static TeamProgress progress() {
        return progress != null ? progress : new TeamProgress(self);
    }

    public static @Nullable Team team() {
        return team;
    }

    public static UUID self() {
        return self;
    }

    public static boolean editing() {
        return editing && canEdit;
    }

    public static boolean canEdit() {
        return canEdit;
    }

    public static boolean teamChat() {
        return teamChat;
    }

    public static List<Invite> invites() {
        return invites;
    }

    public static List<OnlinePlayer> online() {
        return online;
    }

    public static List<OpenParty> openParties() {
        return openParties;
    }

    public static JsonObject editorData() {
        return editorData;
    }

    /** Suggestions for the editor ("advancement", "structure", ...). */
    public static List<String> editorList(String key) {
        List<String> list = new ArrayList<>();
        if (editorData.has(key)) {
            for (JsonElement e : editorData.getAsJsonArray(key)) {
                list.add(e.getAsString());
            }
        }
        return list;
    }

    /** Bumped on every change, so open screens know to refresh. */
    public static int version() {
        return version;
    }

    public static void changed() {
        version++;
    }

    public static QuestLogic logic() {
        return new QuestLogic(book != null ? book : new QuestBook(), progress());
    }

    /** The id of the object most recently created by an edit (consumed once). */
    public static @Nullable String takeLastCreated() {
        String id = lastCreated;
        lastCreated = null;
        return id;
    }

    static void setLastCreated(String id) {
        lastCreated = id;
    }

    public static String lastChapter() {
        return lastChapter;
    }

    public static void setLastChapter(String id) {
        lastChapter = id;
    }

    static void setBook(QuestBook newBook) {
        book = newBook;
        version++;
    }

    static void setState(JsonObject json) {
        self = UUID.fromString(json.get("self").getAsString());
        team = Team.fromClientJson(json.getAsJsonObject("team"));
        progress = TeamProgress.fromJson(json.getAsJsonObject("progress"));
        editing = JsonUtil.getBool(json, "editing", false);
        canEdit = JsonUtil.getBool(json, "can_edit", false);
        teamChat = JsonUtil.getBool(json, "team_chat", false);
        List<Invite> inv = new ArrayList<>();
        for (JsonElement e : json.has("invites") ? json.getAsJsonArray("invites") : new JsonArray()) {
            JsonObject o = e.getAsJsonObject();
            inv.add(new Invite(UUID.fromString(o.get("id").getAsString()), o.get("name").getAsString(), JsonUtil.getInt(o, "color", 0xFFFFFF)));
        }
        invites = List.copyOf(inv);
        List<OnlinePlayer> players = new ArrayList<>();
        for (JsonElement e : json.has("online") ? json.getAsJsonArray("online") : new JsonArray()) {
            JsonObject o = e.getAsJsonObject();
            players.add(new OnlinePlayer(UUID.fromString(o.get("id").getAsString()), o.get("name").getAsString()));
        }
        online = List.copyOf(players);
        List<OpenParty> parties = new ArrayList<>();
        for (JsonElement e : json.has("open_parties") ? json.getAsJsonArray("open_parties") : new JsonArray()) {
            JsonObject o = e.getAsJsonObject();
            parties.add(new OpenParty(UUID.fromString(o.get("id").getAsString()), o.get("name").getAsString(),
                    JsonUtil.getInt(o, "color", 0xFFFFFF), JsonUtil.getInt(o, "members", 0)));
        }
        openParties = List.copyOf(parties);
        version++;
    }

    static void setEditorData(JsonObject json) {
        editorData = json;
        version++;
    }
}
