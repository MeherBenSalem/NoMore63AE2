package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/** Enter a dimension. */
public class DimensionTask extends Task {
    public ResourceLocation dimension = ResourceLocation.withDefaultNamespace("the_nether");

    @Override
    public TaskType<?> type() {
        return TaskTypes.DIMENSION;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.resource("dimension", ResourceKind.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"), () -> dimension, v -> dimension = v);
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(TriggerKind.DIMENSION);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        if (event instanceof TriggerEvent.Dimension dim && dim.to().location().equals(dimension)) {
            ctx.setProgress(1);
        }
    }

    @Override
    public void recheck(TaskContext ctx) {
        if (ctx.player().level().dimension().location().equals(dimension)) {
            ctx.setProgress(1);
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.dimension.title", Component.literal(dimension.toString()));
    }

    @Override
    protected Icon defaultIcon() {
        return switch (dimension.toString()) {
            case "minecraft:the_nether" -> Icon.of(Items.NETHERRACK);
            case "minecraft:the_end" -> Icon.of(Items.END_STONE);
            case "minecraft:overworld" -> Icon.of(Items.GRASS_BLOCK);
            default -> Icon.of(Items.OBSIDIAN);
        };
    }
}
