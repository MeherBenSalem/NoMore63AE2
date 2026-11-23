package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import dev.nightbeam.corerpg.quests.util.Ids;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole quest book: book-wide settings and defaults, chapter groups, chapters (with their quests, links and
 * images) and reward tables. One instance lives on the server; each client holds a synced copy.
 */
public final class QuestBook extends QuestObject {
    // Settings
    public List<String> subtitle = new ArrayList<>();
    public ProgressionMode progressionMode = ProgressionMode.LINEAR;
    public boolean defaultTeamRewards = false;
    public boolean defaultConsumeItems = false;
    public AutoclaimMode defaultAutoclaim = AutoclaimMode.DISABLED;
    public QuestShape defaultQuestShape = QuestShape.CIRCLE;
    public double defaultQuestSize = 1.0;
    public boolean defaultRequireSequentialTasks = false;
    public boolean hideExcludedQuests = true;
    public boolean suppressAllAutoclaim = false;
    public String lockMessage = "";
    public double gridScale = 0.5;
    public boolean pauseGame = false;
    public boolean disableGui = false;
    public int detectionDelay = 20;
    public boolean giveBookOnFirstJoin = true;
    public boolean keepBookOnDeath = true;
    public List<ItemStack> emergencyItems = new ArrayList<>();
    public int emergencyItemsCooldown = 300;
    public boolean lootCrateDrops = true;
    public int version = 1;

    // Content
    public final List<ChapterGroup> groups = new ArrayList<>();
    public final List<Chapter> chapters = new ArrayList<>();
    public final List<RewardTable> tables = new ArrayList<>();

    // Index (rebuilt by rebuildIndex)
    private final Map<String, QuestObject> byId = new HashMap<>();
    private final Map<String, List<Quest>> dependents = new HashMap<>();
    private final Map<TriggerKind, List<Task>> tasksByTrigger = new EnumMap<>(TriggerKind.class);
    private final List<Task> pollingTasks = new ArrayList<>();
    private final List<Quest> allQuests = new ArrayList<>();

    public QuestBook() {
        id = "book";
        title = "&6Quests";
        icon = Icon.of(Items.WRITABLE_BOOK);
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.BOOK;
    }

    @Override
    public QuestBook book() {
        return this;
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.book");
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.text("subtitle", () -> subtitle, v -> subtitle = v);
        p.string("lock_message", "", () -> lockMessage, v -> lockMessage = v);
        p.category("defaults");
        p.enumValue("progression_mode", ProgressionMode.class, ProgressionMode.LINEAR, () -> progressionMode, v -> progressionMode = v == ProgressionMode.DEFAULT ? ProgressionMode.LINEAR : v);
        p.bool("default_team_rewards", false, () -> defaultTeamRewards, v -> defaultTeamRewards = v);
        p.bool("default_consume_items", false, () -> defaultConsumeItems, v -> defaultConsumeItems = v);
        p.enumValue("default_autoclaim", AutoclaimMode.class, AutoclaimMode.DISABLED, () -> defaultAutoclaim, v -> defaultAutoclaim = v == AutoclaimMode.DEFAULT ? AutoclaimMode.DISABLED : v);
        p.enumValue("default_quest_shape", QuestShape.class, QuestShape.CIRCLE, () -> defaultQuestShape, v -> defaultQuestShape = v == QuestShape.DEFAULT ? QuestShape.CIRCLE : v);
        p.decimal("default_quest_size", 1.0, 0.25, 8.0, () -> defaultQuestSize, v -> defaultQuestSize = v);
        p.bool("default_require_sequential_tasks", false, () -> defaultRequireSequentialTasks, v -> defaultRequireSequentialTasks = v);
        p.category("behaviour");
        p.bool("hide_excluded_quests", true, () -> hideExcludedQuests, v -> hideExcludedQuests = v);
        p.bool("suppress_all_autoclaim", false, () -> suppressAllAutoclaim, v -> suppressAllAutoclaim = v);
        p.integer("detection_delay", 20, 1, 1200, () -> detectionDelay, v -> detectionDelay = v);
        p.bool("give_book_on_first_join", true, () -> giveBookOnFirstJoin, v -> giveBookOnFirstJoin = v);
        p.bool("keep_book_on_death", true, () -> keepBookOnDeath, v -> keepBookOnDeath = v);
        p.bool("loot_crate_drops", true, () -> lootCrateDrops, v -> lootCrateDrops = v);
        p.items("emergency_items", () -> emergencyItems, v -> emergencyItems = v);
        p.integer("emergency_items_cooldown", 300, 0, 86400, () -> emergencyItemsCooldown, v -> emergencyItemsCooldown = v);
        p.category("appearance");
        p.decimal("grid_scale", 0.5, 0.0625, 4.0, () -> gridScale, v -> gridScale = v);
        p.bool("pause_game", false, () -> pauseGame, v -> pauseGame = v);
        p.bool("disable_gui", false, () -> disableGui, v -> disableGui = v);
        p.integer("version", 1, 1, Integer.MAX_VALUE, () -> version, v -> version = v).hideInEditor();
    }

    /** The full book in one JSON object (network sync and single-file export). */
    @Override
    protected void writeChildren(JsonObject json, JsonCtx ctx) {
        JsonArray groupArray = new JsonArray();
        groups.forEach(g -> groupArray.add(g.toJson(ctx)));
        json.add("groups", groupArray);
        JsonArray chapterArray = new JsonArray();
        chapters.forEach(c -> chapterArray.add(c.toJson(ctx)));
        json.add("chapters", chapterArray);
        JsonArray tableArray = new JsonArray();
        tables.forEach(t -> tableArray.add(t.toJson(ctx)));
        json.add("reward_tables", tableArray);
    }

    @Override
    protected void readChildren(JsonObject json, JsonCtx ctx) {
        groups.clear();
        chapters.clear();
        tables.clear();
        if (json.has("groups")) {
            json.getAsJsonArray("groups").forEach(e -> {
                ChapterGroup g = new ChapterGroup(this);
                g.setIdInternal(e.getAsJsonObject().get("id").getAsString());
                g.fromJson(e.getAsJsonObject(), ctx);
                groups.add(g);
            });
        }
        if (json.has("chapters")) {
            json.getAsJsonArray("chapters").forEach(e -> chapters.add(Chapter.read(this, e.getAsJsonObject(), ctx)));
        }
        if (json.has("reward_tables")) {
            json.getAsJsonArray("reward_tables").forEach(e -> tables.add(RewardTable.read(this, e.getAsJsonObject(), ctx)));
        }
        rebuildIndex();
    }

    /**
     * Re-links parents and rebuilds every lookup. Duplicate or invalid ids are replaced (with a warning) so the
     * rest of the engine can rely on ids being unique.
     */
    public void rebuildIndex() {
        byId.clear();
        dependents.clear();
        tasksByTrigger.clear();
        pollingTasks.clear();
        allQuests.clear();
        register(this);
        for (ChapterGroup group : groups) {
            group.book = this;
            register(group);
        }
        for (RewardTable table : tables) {
            table.book = this;
            register(table);
            for (RewardTable.Entry entry : table.entries) {
                entry.reward.table = table;
                entry.reward.quest = null;
                register(entry.reward);
            }
        }
        for (Chapter chapter : chapters) {
            chapter.book = this;
            register(chapter);
            for (Quest quest : chapter.quests) {
                quest.chapter = chapter;
                register(quest);
                allQuests.add(quest);
                for (Task task : quest.tasks) {
                    task.quest = quest;
                    register(task);
                    for (TriggerKind trigger : task.triggers()) {
                        tasksByTrigger.computeIfAbsent(trigger, k -> new ArrayList<>()).add(task);
                    }
                    if (task.polls()) {
                        pollingTasks.add(task);
                    }
                }
                for (Reward reward : quest.rewards) {
                    reward.quest = quest;
                    reward.table = null;
                    register(reward);
                }
            }
            for (QuestLink link : chapter.links) {
                link.chapter = chapter;
                register(link);
            }
            for (ChapterImage image : chapter.images) {
                image.chapter = chapter;
                register(image);
            }
        }
        for (Quest quest : allQuests) {
            quest.dependencies.removeIf(dep -> dep.equals(quest.id));
            for (String dep : quest.dependencies) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(quest);
            }
        }
    }

    private void register(QuestObject object) {
        if (!Ids.isValid(object.id) || byId.containsKey(object.id)) {
            String old = object.id;
            String fresh = Ids.unique(byId::containsKey);
            CoreQuests.LOG.warn("Quest book: {} has a {} id '{}'; renamed to '{}'", object.kind().getSerializedName(),
                    Ids.isValid(old) ? "duplicate" : "invalid", old, fresh);
            object.id = fresh;
        }
        byId.put(object.id, object);
    }

    // ---- Lookups ----

    public @Nullable QuestObject get(String id) {
        return byId.get(id);
    }

    public boolean isIdTaken(String id) {
        return byId.containsKey(id);
    }

    public String newId() {
        return Ids.unique(byId::containsKey);
    }

    public <T extends QuestObject> @Nullable T get(String id, Class<T> type) {
        QuestObject o = byId.get(id);
        return type.isInstance(o) ? type.cast(o) : null;
    }

    public @Nullable Quest quest(String id) {
        return get(id, Quest.class);
    }

    public @Nullable Chapter chapter(String id) {
        return get(id, Chapter.class);
    }

    public @Nullable Task task(String id) {
        return get(id, Task.class);
    }

    public @Nullable Reward reward(String id) {
        return get(id, Reward.class);
    }

    public @Nullable RewardTable table(String id) {
        return get(id, RewardTable.class);
    }

    public @Nullable ChapterGroup group(String id) {
        return get(id, ChapterGroup.class);
    }

    public List<Quest> allQuests() {
        return Collections.unmodifiableList(allQuests);
    }

    /** Quests that list {@code questId} as a dependency. */
    public List<Quest> dependents(String questId) {
        return dependents.getOrDefault(questId, List.of());
    }

    public List<Task> tasksFor(TriggerKind trigger) {
        return tasksByTrigger.getOrDefault(trigger, List.of());
    }

    public List<Task> pollingTasks() {
        return Collections.unmodifiableList(pollingTasks);
    }

    public int objectCount() {
        return byId.size();
    }

    /** Chapters grouped for display: ungrouped chapters first, then each group in order. */
    public Map<ChapterGroup, List<Chapter>> chaptersByGroup() {
        Map<ChapterGroup, List<Chapter>> map = new LinkedHashMap<>();
        map.put(null, new ArrayList<>());
        groups.forEach(g -> map.put(g, new ArrayList<>()));
        for (Chapter chapter : chapters) {
            ChapterGroup group = chapter.group.isEmpty() ? null : group(chapter.group);
            map.computeIfAbsent(group, k -> new ArrayList<>()).add(chapter);
        }
        return map;
    }
}
