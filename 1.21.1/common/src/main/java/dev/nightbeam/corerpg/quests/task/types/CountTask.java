package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.Task;

/** Base for tasks that count occurrences of an event. */
public abstract class CountTask extends Task {
    public long value = 1;

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        defineTaskProps(p);
        p.longValue("value", 1, 1, Long.MAX_VALUE / 2, () -> value, v -> value = v);
    }

    protected abstract void defineTaskProps(Props p);

    @Override
    public long maxProgress() {
        return value;
    }
}
