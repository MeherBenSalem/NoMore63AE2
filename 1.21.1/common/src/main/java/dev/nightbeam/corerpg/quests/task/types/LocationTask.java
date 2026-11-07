package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Reach a box in the world (a ruin, a summit, the shrine of a region). */
public class LocationTask extends Task {
    public ResourceLocation dimension = ResourceLocation.withDefaultNamespace("overworld");
    public boolean ignoreDimension = false;
    public int x;
    public int y;
    public int z;
    public int width = 1;
    public int height = 1;
    public int depth = 1;

    @Override
    public TaskType<?> type() {
        return TaskTypes.LOCATION;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.resource("dimension", ResourceKind.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"), () -> dimension, v -> dimension = v);
        p.bool("ignore_dimension", false, () -> ignoreDimension, v -> ignoreDimension = v);
        p.integer("x", 0, -30_000_000, 30_000_000, () -> x, v -> x = v);
        p.integer("y", 0, -2048, 2048, () -> y, v -> y = v);
        p.integer("z", 0, -30_000_000, 30_000_000, () -> z, v -> z = v);
        p.integer("width", 1, 1, 100_000, () -> width, v -> width = v);
        p.integer("height", 1, 1, 4096, () -> height, v -> height = v);
        p.integer("depth", 1, 1, 100_000, () -> depth, v -> depth = v);
    }

    @Override
    public boolean polls() {
        return true;
    }

    @Override
    public void poll(TaskContext ctx) {
        if (isInside(ctx.player())) {
            ctx.setProgress(1);
        }
    }

    public boolean isInside(ServerPlayer player) {
        if (!ignoreDimension && !player.level().dimension().location().equals(dimension)) {
            return false;
        }
        BlockPos pos = player.blockPosition();
        return pos.getX() >= x && pos.getX() < x + width
                && pos.getY() >= y && pos.getY() < y + height
                && pos.getZ() >= z && pos.getZ() < z + depth;
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.location.title", x, y, z);
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.COMPASS);
    }
}
