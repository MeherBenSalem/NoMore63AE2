package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.mixin.BucketItemAccessor;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

import java.util.Locale;

/** Hand in a fluid by the bucket (1000 mB each); the empty buckets are given back. */
public class FluidTask extends Task {
    public ResourceLocation fluid = ResourceLocation.withDefaultNamespace("water");
    public long amount = 1000;

    @Override
    public TaskType<?> type() {
        return TaskTypes.FLUID;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.resource("fluid", ResourceKind.FLUID, ResourceLocation.withDefaultNamespace("water"), () -> fluid, v -> fluid = v);
        p.longValue("amount", 1000, 1, Long.MAX_VALUE / 2, () -> amount, v -> amount = v);
    }

    @Override
    public long maxProgress() {
        return amount;
    }

    @Override
    public boolean canSubmit() {
        return true;
    }

    @Override
    public void submit(TaskContext ctx) {
        Inventory inventory = ctx.player().getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getContainerSize() && ctx.progress() < amount; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BucketItem bucket && matches(((BucketItemAccessor) bucket).corerpg$getContent())) {
                while (!stack.isEmpty() && ctx.progress() < amount) {
                    stack.shrink(1);
                    ctx.addProgress(1000);
                    ItemStack empty = new ItemStack(Items.BUCKET);
                    if (!inventory.add(empty)) {
                        ctx.player().drop(empty, false);
                    }
                    changed = true;
                }
            }
        }
        if (changed) {
            inventory.setChanged();
            ctx.player().containerMenu.broadcastChanges();
        }
    }

    private boolean matches(Fluid content) {
        return BuiltInRegistries.FLUID.getKey(content).equals(fluid);
    }

    @Override
    public Component progressText(long progress) {
        return Component.literal(String.format(Locale.ROOT, "%,d / %,d mB", Math.min(progress, amount), amount));
    }

    @Override
    protected Component defaultTitle() {
        Fluid f = BuiltInRegistries.FLUID.get(fluid);
        Component name = Component.translatable("block." + fluid.getNamespace() + "." + fluid.getPath());
        return Component.translatable("corerpg_quests.task.fluid.title", String.format(Locale.ROOT, "%,d", amount), f == null ? Component.literal(fluid.toString()) : name);
    }

    @Override
    protected Icon defaultIcon() {
        Fluid f = BuiltInRegistries.FLUID.get(fluid);
        return f != null && f.getBucket() != Items.AIR ? Icon.of(f.getBucket()) : Icon.of(Items.BUCKET);
    }
}
