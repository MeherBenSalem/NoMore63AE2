package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.util.Ids;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edit operations on a quest book, as JSON. The editor sends an operation, the server checks and applies it, then
 * sends the same operation to every client, which applies it with this same code. Server and clients therefore end
 * up with identical books.
 */
public final class BookEdits {
    private static final String[] CHILD_ARRAYS = {"quests", "links", "images", "tasks", "rewards", "entries"};

    private BookEdits() {
    }

    // ---- Building operations ----

    public static JsonObject create(ObjectKind kind, String parent, int index, JsonObject data) {
        JsonObject op = op("create");
        op.addProperty("kind", kind.getSerializedName());
        op.addProperty("parent", parent);
        op.addProperty("index", index);
        op.add("data", data);
        return op;
    }

    public static JsonObject update(String id, JsonObject data) {
        JsonObject op = op("update");
        op.addProperty("id", id);
        op.add("data", data);
        return op;
    }

    public static JsonObject delete(String id) {
        JsonObject op = op("delete");
        op.addProperty("id", id);
        return op;
    }

    public static JsonObject move(List<String> ids, double dx, double dy) {
        JsonObject op = op("move");
        op.add("ids", array(ids));
        op.addProperty("dx", dx);
        op.addProperty("dy", dy);
        return op;
    }

    public static JsonObject reorder(String id, int index) {
        JsonObject op = op("reorder");
        op.addProperty("id", id);
        op.addProperty("index", index);
        return op;
    }

    public static JsonObject moveToChapter(List<String> ids, String chapter) {
        JsonObject op = op("move_to_chapter");
        op.add("ids", array(ids));
        op.addProperty("chapter", chapter);
        return op;
    }

    public static JsonObject setBook(JsonObject full) {
        JsonObject op = op("set_book");
        op.add("data", full);
        return op;
    }

    private static JsonObject op(String name) {
        JsonObject op = new JsonObject();
        op.addProperty("op", name);
        return op;
    }

    private static JsonArray array(List<String> ids) {
        JsonArray a = new JsonArray();
        ids.forEach(a::add);
        return a;
    }

    // ---- Server-side preparation ----

    /**
     * Makes sure the object being created (and its children) have ids that are free in the book: ids the editor already
     * chose are kept when free, anything else (missing, "new", taken, duplicated) gets a fresh id. References between
     * the new objects (dependencies, link targets, autofocus) follow renamed ids. Returns the old-to-new id map.
     */
    public static Map<String, String> assignIds(QuestBook book, JsonObject op) {
        Map<String, String> map = new HashMap<>();
        if (!"create".equals(JsonUtil.getString(op, "op", "")) || !op.has("data")) {
            return map;
        }
        Set<String> used = new HashSet<>();
        collect(op.getAsJsonObject("data"), book, map, used);
        remap(op.getAsJsonObject("data"), map);
        return map;
    }

    private static void collect(JsonObject obj, QuestBook book, Map<String, String> map, Set<String> used) {
        String old = JsonUtil.getString(obj, "id", "");
        String fresh;
        if (Ids.isValid(old) && !book.isIdTaken(old) && !old.equals("new") && used.add(old)) {
            // The editor already picked a free id (e.g. when pasting several linked quests): keep it.
            fresh = old;
        } else {
            do {
                fresh = book.newId();
            } while (!used.add(fresh));
        }
        if (!old.isEmpty() && !old.equals(fresh)) {
            map.put(old, fresh);
        }
        obj.addProperty("id", fresh);
        for (String key : CHILD_ARRAYS) {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray(key)) {
                    if (e.isJsonObject()) {
                        collect(e.getAsJsonObject(), book, map, used);
                    }
                }
            }
        }
    }

    private static void remap(JsonObject obj, Map<String, String> map) {
        if (obj.has("dependencies") && obj.get("dependencies").isJsonArray()) {
            JsonArray deps = new JsonArray();
            for (JsonElement e : obj.getAsJsonArray("dependencies")) {
                deps.add(map.getOrDefault(e.getAsString(), e.getAsString()));
            }
            obj.add("dependencies", deps);
        }
        for (String key : new String[]{"quest", "autofocus"}) {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                String v = obj.get(key).getAsString();
                if (map.containsKey(v)) {
                    obj.addProperty(key, map.get(v));
                }
            }
        }
        for (String key : CHILD_ARRAYS) {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray(key)) {
                    if (e.isJsonObject()) {
                        remap(e.getAsJsonObject(), map);
                    }
                }
            }
        }
    }

    // ---- Applying ----

    /** Applies an operation. Returns null on success, or a message saying why it was refused. */
    public static @Nullable String apply(QuestBook book, JsonObject op, JsonCtx ctx) {
        String name = JsonUtil.getString(op, "op", "");
        try {
            String error = switch (name) {
                case "create" -> applyCreate(book, op, ctx);
                case "update" -> applyUpdate(book, op, ctx);
                case "delete" -> applyDelete(book, op);
                case "move" -> applyMove(book, op);
                case "reorder" -> applyReorder(book, op);
                case "move_to_chapter" -> applyMoveToChapter(book, op);
                case "set_book" -> {
                    book.fromJson(op.getAsJsonObject("data"), ctx);
                    yield null;
                }
                default -> "Unknown edit '" + name + "'";
            };
            book.rebuildIndex();
            return error;
        } catch (RuntimeException e) {
            book.rebuildIndex();
            return "Edit failed: " + e.getMessage();
        }
    }

    private static @Nullable String applyCreate(QuestBook book, JsonObject op, JsonCtx ctx) {
        ObjectKind kind = ObjectKind.byName(JsonUtil.getString(op, "kind", ""));
        String parentId = JsonUtil.getString(op, "parent", "");
        int index = JsonUtil.getInt(op, "index", -1);
        JsonObject data = op.getAsJsonObject("data");
        String id = JsonUtil.getString(data, "id", "");
        if (id.isEmpty() || book.isIdTaken(id)) {
            return "The new object needs a free id";
        }
        QuestObject parent = book.get(parentId);
        switch (kind) {
            case GROUP -> {
                ChapterGroup group = new ChapterGroup(book);
                group.setIdInternal(id);
                group.fromJson(data, ctx);
                insert(book.groups, group, index);
            }
            case CHAPTER -> insert(book.chapters, Chapter.read(book, data, ctx), index);
            case TABLE -> insert(book.tables, RewardTable.read(book, data, ctx), index);
            case QUEST -> {
                if (!(parent instanceof Chapter chapter)) {
                    return "A quest must be created in a chapter";
                }
                insert(chapter.quests, Quest.read(chapter, data, ctx), index);
            }
            case LINK -> {
                if (!(parent instanceof Chapter chapter)) {
                    return "A link must be created in a chapter";
                }
                QuestLink link = new QuestLink(chapter);
                link.setIdInternal(id);
                link.fromJson(data, ctx);
                insert(chapter.links, link, index);
            }
            case IMAGE -> {
                if (!(parent instanceof Chapter chapter)) {
                    return "An image must be created in a chapter";
                }
                ChapterImage image = new ChapterImage(chapter);
                image.setIdInternal(id);
                image.fromJson(data, ctx);
                insert(chapter.images, image, index);
            }
            case TASK -> {
                if (!(parent instanceof Quest quest)) {
                    return "A task must be created in a quest";
                }
                Task task = TaskTypes.read(data, ctx);
                task.quest = quest;
                insert(quest.tasks, task, index);
            }
            case REWARD -> {
                Reward reward = RewardTypes.read(data, ctx);
                if (parent instanceof Quest quest) {
                    reward.quest = quest;
                    insert(quest.rewards, reward, index);
                } else if (parent instanceof RewardTable table) {
                    reward.table = table;
                    double weight = data.has("weight") ? data.get("weight").getAsDouble() : 1.0;
                    RewardTable.Entry entry = new RewardTable.Entry(Math.max(0, weight), reward);
                    insert(table.entries, entry, index);
                } else {
                    return "A reward must be created in a quest or a reward table";
                }
            }
            default -> {
                return "Cannot create a " + kind.getSerializedName();
            }
        }
        return null;
    }

    private static @Nullable String applyUpdate(QuestBook book, JsonObject op, JsonCtx ctx) {
        QuestObject object = book.get(JsonUtil.getString(op, "id", ""));
        if (object == null) {
            return "No such object";
        }
        JsonObject data = op.getAsJsonObject("data");
        object.readProps(data, ctx);
        if (object instanceof Reward reward && reward.table != null && data.has("weight")) {
            for (RewardTable.Entry entry : reward.table.entries) {
                if (entry.reward == reward) {
                    entry.weight = Math.max(0, data.get("weight").getAsDouble());
                }
            }
        }
        return null;
    }

    private static @Nullable String applyDelete(QuestBook book, JsonObject op) {
        String id = JsonUtil.getString(op, "id", "");
        QuestObject object = book.get(id);
        if (object == null) {
            return "No such object";
        }
        switch (object) {
            case QuestBook ignored -> {
                return "The book cannot be deleted";
            }
            case ChapterGroup group -> {
                book.groups.remove(group);
                for (Chapter c : book.chapters) {
                    if (c.group.equals(id)) {
                        c.group = "";
                    }
                }
            }
            case Chapter chapter -> book.chapters.remove(chapter);
            case RewardTable table -> book.tables.remove(table);
            case Quest quest -> {
                quest.chapter().quests.remove(quest);
                for (Chapter c : book.chapters) {
                    c.links.removeIf(l -> l.quest.equals(id));
                    if (c.autofocus.equals(id)) {
                        c.autofocus = "";
                    }
                    for (Quest q : c.quests) {
                        q.dependencies.remove(id);
                    }
                }
            }
            case QuestLink link -> link.chapter().links.remove(link);
            case ChapterImage image -> image.chapter().images.remove(image);
            case Task task -> {
                if (task.quest != null) {
                    task.quest.tasks.remove(task);
                }
            }
            case Reward reward -> {
                if (reward.quest != null) {
                    reward.quest.rewards.remove(reward);
                } else if (reward.table != null) {
                    reward.table.entries.removeIf(e -> e.reward == reward);
                }
            }
            default -> {
                return "Cannot delete " + object.describe();
            }
        }
        return null;
    }

    private static @Nullable String applyMove(QuestBook book, JsonObject op) {
        double dx = op.get("dx").getAsDouble();
        double dy = op.get("dy").getAsDouble();
        for (JsonElement e : op.getAsJsonArray("ids")) {
            QuestObject object = book.get(e.getAsString());
            switch (object) {
                case Quest q -> {
                    q.x += dx;
                    q.y += dy;
                }
                case QuestLink l -> {
                    l.x += dx;
                    l.y += dy;
                }
                case ChapterImage i -> {
                    i.x += dx;
                    i.y += dy;
                }
                case null, default -> {
                }
            }
        }
        return null;
    }

    private static @Nullable String applyReorder(QuestBook book, JsonObject op) {
        QuestObject object = book.get(JsonUtil.getString(op, "id", ""));
        int index = JsonUtil.getInt(op, "index", 0);
        switch (object) {
            case Chapter c -> move(book.chapters, c, index);
            case ChapterGroup g -> move(book.groups, g, index);
            case RewardTable t -> move(book.tables, t, index);
            case Task t when t.quest != null -> move(t.quest.tasks, t, index);
            case Reward r when r.quest != null -> move(r.quest.rewards, r, index);
            case null, default -> {
                return "Cannot reorder that";
            }
        }
        return null;
    }

    private static @Nullable String applyMoveToChapter(QuestBook book, JsonObject op) {
        Chapter target = book.chapter(JsonUtil.getString(op, "chapter", ""));
        if (target == null) {
            return "No such chapter";
        }
        for (JsonElement e : op.getAsJsonArray("ids")) {
            QuestObject object = book.get(e.getAsString());
            switch (object) {
                case Quest q when q.chapter() != target -> {
                    q.chapter().quests.remove(q);
                    q.chapter = target;
                    target.quests.add(q);
                }
                case QuestLink l when l.chapter() != target -> {
                    l.chapter().links.remove(l);
                    l.chapter = target;
                    target.links.add(l);
                }
                case ChapterImage i when i.chapter() != target -> {
                    i.chapter().images.remove(i);
                    i.chapter = target;
                    target.images.add(i);
                }
                case null, default -> {
                }
            }
        }
        return null;
    }

    private static <T> void insert(List<T> list, T value, int index) {
        if (index < 0 || index > list.size()) {
            list.add(value);
        } else {
            list.add(index, value);
        }
    }

    private static <T> void move(List<T> list, T value, int index) {
        if (list.remove(value)) {
            insert(list, value, Math.max(0, Math.min(index, list.size())));
        }
    }
}
