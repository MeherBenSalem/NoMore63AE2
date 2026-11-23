package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Tristate;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Have (or hand in) items. Without consuming, the task completes on its own once the team member carries enough.
 * With consuming, the player hands items in from the quest book; progress is kept between hand-ins.
 */
public class ItemTask extends ItemCriteriaTask {
    public Tristate consumeItems = Tristate.DEFAULT;
    public boolean onlyFromCrafting = false;

    @Override
    public TaskType<?> type() {
        return TaskTypes.ITEM;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.enumValue("consume_items", Tristate.class, Tristate.DEFAULT, () -> consumeItems, v -> consumeItems = v);
        p.bool("only_from_crafting", false, () -> onlyFromCrafting, v -> onlyFromCrafting = v);
    }

    public boolean consumes() {
        boolean chapterDefault = quest != null ? quest.chapter().consumeItems.get(book().defaultConsumeItems) : false;
        return consumeItems.get(chapterDefault);
    }

    @Override
    public Set<TriggerKind> triggers() {
        if (onlyFromCrafting) {
            return EnumSet.of(TriggerKind.CRAFT);
        }
        return consumes() ? EnumSet.noneOf(TriggerKind.class) : EnumSet.of(TriggerKind.INVENTORY);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        if (event instanceof TriggerEvent.Craft craft) {
            if (test(craft.stack())) {
                ctx.addProgress(craft.amount());
            }
        } else if (event instanceof TriggerEvent.Inventory) {
            detect(ctx);
        }
    }

    @Override
    public void recheck(TaskContext ctx) {
        if (!onlyFromCrafting && !consumes()) {
            detect(ctx);
        }
    }

    private void detect(TaskContext ctx) {
        long carried = countCarried(ctx.player());
        if (carried > ctx.progress()) {
            ctx.setProgress(Math.min(carried, count));
        }
    }

    public long countCarried(ServerPlayer player) {
        long total = 0;
        for (ItemStack stack : allSlots(player.getInventory())) {
            if (test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean canSubmit() {
        return consumes() && !onlyFromCrafting;
    }

    @Override
    public void submit(TaskContext ctx) {
        long needed = count - ctx.progress();
        if (needed <= 0) {
            return;
        }
        Inventory inventory = ctx.player().getInventory();
        long taken = 0;
        for (ItemStack stack : allSlots(inventory)) {
            if (taken >= needed) {
                break;
            }
            if (test(stack)) {
                int take = (int) Math.min(stack.getCount(), needed - taken);
                stack.shrink(take);
                taken += take;
            }
        }
        if (taken > 0) {
            inventory.setChanged();
            ctx.player().containerMenu.broadcastChanges();
            ctx.addProgress(taken);
        }
    }

    @Override
    public Component submitLabel() {
        return Component.translatable("corerpg_quests.task.hand_in");
    }

    @Override
    public void addTooltip(List<Component> lines) {
        super.addTooltip(lines);
        if (canSubmit()) {
            lines.add(Component.translatable("corerpg_quests.task.item.consumes"));
        }
        if (onlyFromCrafting) {
            lines.add(Component.translatable("corerpg_quests.task.item.only_from_crafting"));
        }
    }

    static List<ItemStack> allSlots(Inventory inventory) {
        List<ItemStack> list = new java.util.ArrayList<>(inventory.items.size() + 5);
        list.addAll(inventory.items);
        list.addAll(inventory.armor);
        list.addAll(inventory.offhand);
        return list;
    }
}
