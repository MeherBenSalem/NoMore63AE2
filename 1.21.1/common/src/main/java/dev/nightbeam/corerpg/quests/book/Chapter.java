package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** A page of the quest book: a canvas of quests (plus links to quests elsewhere and decorative images). */
public final class Chapter extends QuestObject {
    QuestBook book;
    public List<String> subtitle = new ArrayList<>();
    public String group = "";
    public boolean alwaysInvisible = false;
    public QuestShape defaultQuestShape = QuestShape.DEFAULT;
    public double defaultQuestSize = 0;
    public boolean defaultHideDependencyLines = false;
    public ProgressionMode progressionMode = ProgressionMode.DEFAULT;
    public Tristate consumeItems = Tristate.DEFAULT;
    public Tristate requireSequentialTasks = Tristate.DEFAULT;
    public boolean hideQuestDetailsUntilStartable = false;
    public boolean hideQuestUntilDepsComplete = false;
    public boolean hideQuestUntilDepsVisible = false;
    public boolean hideTextUntilComplete = false;
    public String autofocus = "";
    public int defaultMinWidth = 0;

    public final List<Quest> quests = new ArrayList<>();
    public final List<QuestLink> links = new ArrayList<>();
    public final List<ChapterImage> images = new ArrayList<>();

    public Chapter(QuestBook book) {
        this.book = book;
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.CHAPTER;
    }

    @Override
    public QuestBook book() {
        return book;
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.chapter");
    }

    @Override
    protected Icon defaultIcon() {
        for (Quest quest : quests) {
            Icon icon = quest.displayIcon();
            if (!icon.isEmpty()) {
                return icon;
            }
        }
        return Icon.of(Items.BOOK);
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.text("subtitle", () -> subtitle, v -> subtitle = v);
        p.id("group", () -> group, v -> group = v);
        p.category("appearance");
        p.enumValue("default_quest_shape", QuestShape.class, QuestShape.DEFAULT, () -> defaultQuestShape, v -> defaultQuestShape = v);
        p.decimal("default_quest_size", 0, 0, 8, () -> defaultQuestSize, v -> defaultQuestSize = v);
        p.integer("default_min_width", 0, 0, 3000, () -> defaultMinWidth, v -> defaultMinWidth = v);
        p.bool("default_hide_dependency_lines", false, () -> defaultHideDependencyLines, v -> defaultHideDependencyLines = v);
        p.category("visibility");
        p.bool("always_invisible", false, () -> alwaysInvisible, v -> alwaysInvisible = v);
        p.bool("hide_quest_details_until_startable", false, () -> hideQuestDetailsUntilStartable, v -> hideQuestDetailsUntilStartable = v);
        p.bool("hide_quest_until_deps_complete", false, () -> hideQuestUntilDepsComplete, v -> hideQuestUntilDepsComplete = v);
        p.bool("hide_quest_until_deps_visible", false, () -> hideQuestUntilDepsVisible, v -> hideQuestUntilDepsVisible = v);
        p.bool("hide_text_until_complete", false, () -> hideTextUntilComplete, v -> hideTextUntilComplete = v);
        p.category("misc");
        p.enumValue("progression_mode", ProgressionMode.class, ProgressionMode.DEFAULT, () -> progressionMode, v -> progressionMode = v);
        p.enumValue("consume_items", Tristate.class, Tristate.DEFAULT, () -> consumeItems, v -> consumeItems = v);
        p.enumValue("require_sequential_tasks", Tristate.class, Tristate.DEFAULT, () -> requireSequentialTasks, v -> requireSequentialTasks = v);
        p.id("autofocus", () -> autofocus, v -> autofocus = v);
    }

    @Override
    protected void writeChildren(JsonObject json, JsonCtx ctx) {
        JsonArray questArray = new JsonArray();
        quests.forEach(q -> questArray.add(q.toJson(ctx)));
        json.add("quests", questArray);
        if (!links.isEmpty()) {
            JsonArray linkArray = new JsonArray();
            links.forEach(l -> linkArray.add(l.toJson(ctx)));
            json.add("links", linkArray);
        }
        if (!images.isEmpty()) {
            JsonArray imageArray = new JsonArray();
            images.forEach(i -> imageArray.add(i.toJson(ctx)));
            json.add("images", imageArray);
        }
    }

    @Override
    protected void readChildren(JsonObject json, JsonCtx ctx) {
        quests.clear();
        links.clear();
        images.clear();
        if (json.has("quests")) {
            json.getAsJsonArray("quests").forEach(e -> quests.add(Quest.read(this, e.getAsJsonObject(), ctx)));
        }
        if (json.has("links")) {
            json.getAsJsonArray("links").forEach(e -> {
                QuestLink link = new QuestLink(this);
                link.setIdInternal(e.getAsJsonObject().get("id").getAsString());
                link.fromJson(e.getAsJsonObject(), ctx);
                links.add(link);
            });
        }
        if (json.has("images")) {
            json.getAsJsonArray("images").forEach(e -> {
                ChapterImage image = new ChapterImage(this);
                image.setIdInternal(e.getAsJsonObject().get("id").getAsString());
                image.fromJson(e.getAsJsonObject(), ctx);
                images.add(image);
            });
        }
    }

    public static Chapter read(QuestBook book, JsonObject json, JsonCtx ctx) {
        Chapter chapter = new Chapter(book);
        chapter.setIdInternal(json.has("id") ? json.get("id").getAsString() : "");
        chapter.fromJson(json, ctx);
        return chapter;
    }

    public QuestShape effectiveShape() {
        return defaultQuestShape == QuestShape.DEFAULT ? book.defaultQuestShape : defaultQuestShape;
    }

    public double effectiveQuestSize() {
        return defaultQuestSize > 0 ? defaultQuestSize : book.defaultQuestSize;
    }

    public ProgressionMode effectiveProgressionMode() {
        return progressionMode.or(book.progressionMode);
    }
}
