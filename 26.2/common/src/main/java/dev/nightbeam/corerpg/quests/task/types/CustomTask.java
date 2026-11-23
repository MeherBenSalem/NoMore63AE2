package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/**
 * Progressed from outside: {@code /corequests custom <player> <key> [amount]}, command blocks, datapack functions,
 * scripts or other mods through the API. Every custom task with the same key advances together.
 */
public class CustomTask extends CountTask {
    public String key = "";

    @Override
    public TaskType<?> type() {
        return TaskTypes.CUSTOM;
    }

    @Override
    protected void defineTaskProps(Props p) {
        p.string("key", "", () -> key, v -> key = v);
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(TriggerKind.CUSTOM);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        if (event instanceof TriggerEvent.Custom custom && custom.key().equals(key)) {
            if (custom.amount() < 0) {
                ctx.setProgress(Math.max(0, ctx.progress() + custom.amount()));
            } else {
                ctx.addProgress(custom.amount());
            }
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.literal(key.isBlank() ? "?" : key);
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.COMMAND_BLOCK);
    }
}
