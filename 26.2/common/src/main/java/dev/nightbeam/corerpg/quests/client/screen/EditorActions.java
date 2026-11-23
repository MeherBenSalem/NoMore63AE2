package dev.nightbeam.corerpg.quests.client.screen;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.BookEdits;
import dev.nightbeam.corerpg.quests.book.ObjectKind;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.util.JsonUtil;

import java.util.List;

/** Sends editor operations to the server; the server applies them and replays them on every client. */
public final class EditorActions {
    private static String pendingFocus = "";

    private EditorActions() {
    }

    public static void send(JsonObject op) {
        if (ClientQuests.editing()) {
            Services.PLATFORM.sendToServer(new Payloads.RequestEdit(JsonUtil.gzip(JsonUtil.COMPACT.toJson(op))));
        }
    }

    /** Creates an object; {@code data} may omit the id (the server picks one). */
    public static void create(ObjectKind kind, String parent, JsonObject data) {
        create(kind, parent, -1, data);
    }

    public static void create(ObjectKind kind, String parent, int index, JsonObject data) {
        if (!data.has("id")) {
            data.addProperty("id", "new");
        }
        send(BookEdits.create(kind, parent, index, data));
    }

    public static void update(QuestObject object) {
        send(BookEdits.update(object.id(), object.propsToJson(ClientQuests.ctx())));
    }

    public static void update(String id, JsonObject data) {
        send(BookEdits.update(id, data));
    }

    public static void delete(String id) {
        send(BookEdits.delete(id));
    }

    public static void move(List<String> ids, double dx, double dy) {
        if (!ids.isEmpty() && (dx != 0 || dy != 0)) {
            send(BookEdits.move(ids, dx, dy));
        }
    }

    public static void reorder(String id, int index) {
        send(BookEdits.reorder(id, index));
    }

    public static void moveToChapter(List<String> ids, String chapter) {
        send(BookEdits.moveToChapter(ids, chapter));
    }

    public static void changeProgress(String id, boolean complete) {
        Services.PLATFORM.sendToServer(new Payloads.ChangeProgress(id, complete));
    }

    /** Remember to open the next created object when it arrives. */
    public static void focusNextCreated(String hint) {
        pendingFocus = hint;
    }

    public static String takePendingFocus() {
        String s = pendingFocus;
        pendingFocus = "";
        return s;
    }

    public static boolean hasPendingFocus() {
        return !pendingFocus.isEmpty();
    }
}
