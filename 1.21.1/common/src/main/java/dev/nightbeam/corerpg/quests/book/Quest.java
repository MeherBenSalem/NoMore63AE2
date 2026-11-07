package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** One quest: where it sits on the chapter canvas, what unlocks it, what it asks for and what it gives. */
public final class Quest extends QuestObject {
    Chapter chapter;

    // Text
    public String subtitle = "";
    public List<String> description = new ArrayList<>();
    public String guidePage = "";

    // Appearance
    public double x;
    public double y;
    public QuestShape shape = QuestShape.DEFAULT;
    public double size = 0;
    public double iconScale = 1.0;
    public int minWidth = 0;

    // Dependencies
    public List<String> dependencies = new ArrayList<>();
    public DependencyMode dependencyMode = DependencyMode.ALL_COMPLETED;
    public int minRequiredDependencies = 0;
    public int maxCompletableDependents = 0;
    public Tristate hideDependencyLines = Tristate.DEFAULT;
    public boolean hideDependentLines = false;

    // Visibility
    public boolean invisible = false;
    public int invisibleUntilTasks = 0;
    public Tristate hideUntilDepsComplete = Tristate.DEFAULT;
    public Tristate hideUntilDepsVisible = Tristate.DEFAULT;
    public Tristate hideTextUntilComplete = Tristate.DEFAULT;
    public Tristate hideDetailsUntilStartable = Tristate.DEFAULT;
    public boolean hideLockIcon = false;

    // Behaviour
    public boolean optional = false;
    public boolean repeatable = false;
    public long repeatCooldown = 0;
    public ProgressionMode progressionMode = ProgressionMode.DEFAULT;
    public Tristate requireSequentialTasks = Tristate.DEFAULT;
    public boolean disableCompletionToast = false;
    public String completionCommand = "";
    public boolean ignoreRewardBlocking = false;

    public final List<Task> tasks = new ArrayList<>();
    public final List<Reward> rewards = new ArrayList<>();

    public Quest(Chapter chapter) {
        this.chapter = chapter;
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.QUEST;
    }

    @Override
    public QuestBook book() {
        return chapter == null ? null : chapter.book();
    }

    public Chapter chapter() {
        return chapter;
    }

    @Override
    protected Component defaultTitle() {
        return tasks.isEmpty() ? Component.translatable("corerpg_quests.quest") : tasks.getFirst().displayTitle();
    }

    @Override
    protected Icon defaultIcon() {
        for (Task task : tasks) {
            Icon icon = task.displayIcon();
            if (!icon.isEmpty()) {
                return icon;
            }
        }
        return Icon.EMPTY;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.string("subtitle", "", () -> subtitle, v -> subtitle = v);
        p.text("description", () -> description, v -> description = v);
        p.string("guide_page", "", () -> guidePage, v -> guidePage = v);
        p.category("appearance");
        p.decimal("x", 0, -100000, 100000, () -> x, v -> x = v);
        p.decimal("y", 0, -100000, 100000, () -> y, v -> y = v);
        p.enumValue("shape", QuestShape.class, QuestShape.DEFAULT, () -> shape, v -> shape = v);
        p.decimal("size", 0, 0, 8, () -> size, v -> size = v);
        p.decimal("icon_scale", 1, 0.1, 4, () -> iconScale, v -> iconScale = v);
        p.integer("min_width", 0, 0, 3000, () -> minWidth, v -> minWidth = v);
        p.category("dependencies");
        p.idList("dependencies", () -> dependencies, v -> dependencies = v);
        p.enumValue("dependency_mode", DependencyMode.class, DependencyMode.ALL_COMPLETED, () -> dependencyMode, v -> dependencyMode = v);
        p.integer("min_required_dependencies", 0, 0, 1000, () -> minRequiredDependencies, v -> minRequiredDependencies = v);
        p.integer("max_completable_dependents", 0, 0, 1000, () -> maxCompletableDependents, v -> maxCompletableDependents = v);
        p.enumValue("hide_dependency_lines", Tristate.class, Tristate.DEFAULT, () -> hideDependencyLines, v -> hideDependencyLines = v);
        p.bool("hide_dependent_lines", false, () -> hideDependentLines, v -> hideDependentLines = v);
        p.category("visibility");
        p.bool("invisible", false, () -> invisible, v -> invisible = v);
        p.integer("invisible_until_tasks", 0, 0, 1000, () -> invisibleUntilTasks, v -> invisibleUntilTasks = v);
        p.enumValue("hide_until_deps_complete", Tristate.class, Tristate.DEFAULT, () -> hideUntilDepsComplete, v -> hideUntilDepsComplete = v);
        p.enumValue("hide_until_deps_visible", Tristate.class, Tristate.DEFAULT, () -> hideUntilDepsVisible, v -> hideUntilDepsVisible = v);
        p.enumValue("hide_text_until_complete", Tristate.class, Tristate.DEFAULT, () -> hideTextUntilComplete, v -> hideTextUntilComplete = v);
        p.enumValue("hide_details_until_startable", Tristate.class, Tristate.DEFAULT, () -> hideDetailsUntilStartable, v -> hideDetailsUntilStartable = v);
        p.bool("hide_lock_icon", false, () -> hideLockIcon, v -> hideLockIcon = v);
        p.category("misc");
        p.bool("optional", false, () -> optional, v -> optional = v);
        p.bool("repeatable", false, () -> repeatable, v -> repeatable = v);
        p.longValue("repeat_cooldown", 0, 0, Long.MAX_VALUE / 1000, () -> repeatCooldown, v -> repeatCooldown = v);
        p.enumValue("progression_mode", ProgressionMode.class, ProgressionMode.DEFAULT, () -> progressionMode, v -> progressionMode = v);
        p.enumValue("require_sequential_tasks", Tristate.class, Tristate.DEFAULT, () -> requireSequentialTasks, v -> requireSequentialTasks = v);
        p.bool("disable_completion_toast", false, () -> disableCompletionToast, v -> disableCompletionToast = v);
        p.string("completion_command", "", () -> completionCommand, v -> completionCommand = v);
        p.bool("ignore_reward_blocking", false, () -> ignoreRewardBlocking, v -> ignoreRewardBlocking = v);
    }

    @Override
    protected void writeChildren(JsonObject json, JsonCtx ctx) {
        JsonArray taskArray = new JsonArray();
        tasks.forEach(t -> taskArray.add(t.toJson(ctx)));
        json.add("tasks", taskArray);
        JsonArray rewardArray = new JsonArray();
        rewards.forEach(r -> rewardArray.add(r.toJson(ctx)));
        json.add("rewards", rewardArray);
    }

    @Override
    protected void readChildren(JsonObject json, JsonCtx ctx) {
        tasks.clear();
        rewards.clear();
        if (json.has("tasks")) {
            for (var e : json.getAsJsonArray("tasks")) {
                Task task = TaskTypes.read(e.getAsJsonObject(), ctx);
                if (task == null) {
                    CoreQuests.LOG.warn("Quest '{}': skipped a task of unknown type {}", id, e.getAsJsonObject().get("type"));
                    continue;
                }
                task.quest = this;
                tasks.add(task);
            }
        }
        if (json.has("rewards")) {
            for (var e : json.getAsJsonArray("rewards")) {
                Reward reward = RewardTypes.read(e.getAsJsonObject(), ctx);
                if (reward == null) {
                    CoreQuests.LOG.warn("Quest '{}': skipped a reward of unknown type {}", id, e.getAsJsonObject().get("type"));
                    continue;
                }
                reward.quest = this;
                rewards.add(reward);
            }
        }
    }

    public static Quest read(Chapter chapter, JsonObject json, JsonCtx ctx) {
        Quest quest = new Quest(chapter);
        quest.setIdInternal(json.has("id") ? json.get("id").getAsString() : "");
        quest.fromJson(json, ctx);
        return quest;
    }

    // ---- Effective values (quest override -> chapter default -> book default) ----

    public QuestShape effectiveShape() {
        return shape == QuestShape.DEFAULT ? chapter.effectiveShape() : shape;
    }

    public double effectiveSize() {
        return size > 0 ? size : chapter.effectiveQuestSize();
    }

    public ProgressionMode effectiveProgressionMode() {
        return progressionMode.or(chapter.effectiveProgressionMode());
    }

    public boolean effectiveRequireSequentialTasks() {
        return requireSequentialTasks.get(chapter.requireSequentialTasks.get(book().defaultRequireSequentialTasks));
    }

    public boolean effectiveHideDependencyLines() {
        return hideDependencyLines.get(chapter.defaultHideDependencyLines);
    }

    public boolean effectiveHideUntilDepsComplete() {
        return hideUntilDepsComplete.get(chapter.hideQuestUntilDepsComplete);
    }

    public boolean effectiveHideUntilDepsVisible() {
        return hideUntilDepsVisible.get(chapter.hideQuestUntilDepsVisible);
    }

    public boolean effectiveHideTextUntilComplete() {
        return hideTextUntilComplete.get(chapter.hideTextUntilComplete);
    }

    public boolean effectiveHideDetailsUntilStartable() {
        return hideDetailsUntilStartable.get(chapter.hideQuestDetailsUntilStartable);
    }

    public int effectiveMinWidth() {
        return minWidth > 0 ? minWidth : chapter.defaultMinWidth;
    }

    /** Tasks that must be finished for the quest to complete (optional tasks are excluded, unless all are optional). */
    public List<Task> requiredTasks() {
        List<Task> required = new ArrayList<>();
        for (Task task : tasks) {
            if (!task.optionalTask) {
                required.add(task);
            }
        }
        return required.isEmpty() ? tasks : required;
    }
}
