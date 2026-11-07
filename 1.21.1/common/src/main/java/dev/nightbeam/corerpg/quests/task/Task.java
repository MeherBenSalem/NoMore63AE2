package dev.nightbeam.corerpg.quests.task;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.book.ObjectKind;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Something a quest asks for. A task is event driven (it lists {@link #triggers()}), polled every few ticks
 * ({@link #polls()}), submitted by a click in the quest book ({@link #canSubmit()}), or a mix of these.
 */
public abstract class Task extends dev.nightbeam.corerpg.quests.book.QuestObject {
    public Quest quest;
    public boolean optionalTask = false;

    @Override
    public final ObjectKind kind() {
        return ObjectKind.TASK;
    }

    @Override
    public QuestBook book() {
        return quest == null ? null : quest.book();
    }

    public abstract TaskType<?> type();

    @Override
    protected void writeType(JsonObject json) {
        json.addProperty("type", type().shortId());
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.bool("optional_task", false, () -> optionalTask, v -> optionalTask = v);
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable(type().translationKey());
    }

    @Override
    protected Icon defaultIcon() {
        return type().icon();
    }

    /** The progress needed to complete the task. */
    public long maxProgress() {
        return 1;
    }

    public Set<TriggerKind> triggers() {
        return EnumSet.noneOf(TriggerKind.class);
    }

    /** True when the task is checked every {@code detection_delay} ticks for each online player. */
    public boolean polls() {
        return false;
    }

    public void onTrigger(TaskContext ctx, TriggerEvent event) {
    }

    public void poll(TaskContext ctx) {
    }

    /**
     * Checks the current game state once, when the task becomes active or the player joins (an advancement already
     * earned, items already carried, a stage already owned...).
     */
    public void recheck(TaskContext ctx) {
        if (polls()) {
            poll(ctx);
        }
    }

    /** Whether the quest book shows a submit/check button for this task. */
    public boolean canSubmit() {
        return false;
    }

    public void submit(TaskContext ctx) {
    }

    /** "3 / 10" style progress for the quest book; empty when the task is a single yes/no. */
    public Component progressText(long progress) {
        if (maxProgress() <= 1) {
            return Component.empty();
        }
        return Component.literal(TextUtil.compact(Math.min(progress, maxProgress())) + " / " + TextUtil.compact(maxProgress()));
    }

    /** Extra tooltip lines shown when hovering the task in the quest book. */
    public void addTooltip(List<Component> lines) {
    }

    /** The button label for submittable tasks. */
    public Component submitLabel() {
        return Component.translatable("corerpg_quests.task.submit");
    }
}
