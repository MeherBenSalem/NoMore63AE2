package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Items;

/** Reach a value in a vanilla custom statistic (jumps, distance walked, play time, raids won...). */
public class StatTask extends CountTask {
    public ResourceLocation stat = ResourceLocation.withDefaultNamespace("jump");

    @Override
    public TaskType<?> type() {
        return TaskTypes.STAT;
    }

    @Override
    protected void defineTaskProps(Props p) {
        p.resource("stat", ResourceKind.STAT, ResourceLocation.withDefaultNamespace("jump"), () -> stat, v -> stat = v);
    }

    @Override
    public boolean polls() {
        return true;
    }

    @Override
    public void poll(TaskContext ctx) {
        if (!BuiltInRegistries.CUSTOM_STAT.containsKey(stat)) {
            return;
        }
        ResourceLocation registered = BuiltInRegistries.CUSTOM_STAT.get(stat);
        long current = ctx.player().getStats().getValue(Stats.CUSTOM.get(registered));
        if (current > ctx.progress()) {
            ctx.setProgress(Math.min(current, value));
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.stat.title",
                Component.translatable("stat." + stat.toString().replace(':', '.')), value);
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.PAPER);
    }
}
