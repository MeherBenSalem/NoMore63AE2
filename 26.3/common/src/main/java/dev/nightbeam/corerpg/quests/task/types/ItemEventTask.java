package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/**
 * Counts items involved in an action: crafted (crafting table, furnace, smithing...), eaten or drunk, fished up,
 * bought from villagers, or enchanted. Leaving the item empty accepts any item.
 */
public class ItemEventTask extends ItemCriteriaTask {
    private final TaskType<?> type;
    private final TriggerKind trigger;
    private final Item fallbackIcon;
    public int minLevels = 0;

    private ItemEventTask(TaskType<?> type, TriggerKind trigger, Item fallbackIcon) {
        this.type = type;
        this.trigger = trigger;
        this.fallbackIcon = fallbackIcon;
    }

    public static ItemEventTask craft() {
        return new ItemEventTask(TaskTypes.CRAFT, TriggerKind.CRAFT, Items.CRAFTING_TABLE);
    }

    public static ItemEventTask consume() {
        return new ItemEventTask(TaskTypes.CONSUME, TriggerKind.CONSUME, Items.APPLE);
    }

    public static ItemEventTask fish() {
        return new ItemEventTask(TaskTypes.FISH, TriggerKind.FISH, Items.FISHING_ROD);
    }

    public static ItemEventTask trade() {
        return new ItemEventTask(TaskTypes.TRADE, TriggerKind.TRADE, Items.EMERALD);
    }

    public static ItemEventTask enchant() {
        return new ItemEventTask(TaskTypes.ENCHANT, TriggerKind.ENCHANT, Items.ENCHANTING_TABLE);
    }

    @Override
    public TaskType<?> type() {
        return type;
    }

    @Override
    protected boolean anyItemAllowed() {
        return true;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        if (trigger == TriggerKind.ENCHANT) {
            p.integer("min_levels", 0, 0, 1000, () -> minLevels, v -> minLevels = v);
        }
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(trigger);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        switch (event) {
            case TriggerEvent.Craft craft -> {
                if (test(craft.stack())) {
                    ctx.addProgress(Math.max(1, craft.amount()));
                }
            }
            case TriggerEvent.Consume consume -> {
                if (test(consume.stack())) {
                    ctx.addProgress(1);
                }
            }
            case TriggerEvent.Fish fish -> {
                for (ItemStack stack : fish.items()) {
                    if (test(stack)) {
                        ctx.addProgress(stack.getCount());
                    }
                }
            }
            case TriggerEvent.Trade tradeEvent -> {
                if (test(tradeEvent.result())) {
                    ctx.addProgress(1);
                }
            }
            case TriggerEvent.Enchant enchantEvent -> {
                if (enchantEvent.levels() >= minLevels && test(enchantEvent.stack())) {
                    ctx.addProgress(1);
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task." + type.id().getPath() + ".title", count, itemName());
    }

    @Override
    protected Icon defaultIcon() {
        return item.isEmpty() ? Icon.of(fallbackIcon) : Icon.of(item);
    }
}
