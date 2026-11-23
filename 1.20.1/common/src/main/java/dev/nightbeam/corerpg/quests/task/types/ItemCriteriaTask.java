package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.ComponentMatch;
import dev.nightbeam.corerpg.quests.task.ItemMatcher;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Shared base for tasks that count items matching an item, a smart filter item or a filter expression. */
public abstract class ItemCriteriaTask extends Task {
    public ItemStack item = ItemStack.EMPTY;
    public String filter = "";
    public ComponentMatch matchComponents = ComponentMatch.FUZZY;
    public long count = 1;

    protected boolean anyItemAllowed() {
        return false;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.item("item", () -> item, v -> item = v);
        p.filter("filter", () -> filter, v -> filter = v);
        p.enumValue("match_components", ComponentMatch.class, ComponentMatch.FUZZY, () -> matchComponents, v -> matchComponents = v);
        p.longValue("count", 1, 1, Long.MAX_VALUE / 2, () -> count, v -> count = v);
    }

    public boolean test(ItemStack stack) {
        if (item.isEmpty() && filter.isBlank()) {
            return anyItemAllowed() && !stack.isEmpty();
        }
        return ItemMatcher.matches(item, filter, matchComponents, stack);
    }

    @Override
    public long maxProgress() {
        return count;
    }

    @Override
    protected Icon defaultIcon() {
        return item.isEmpty() ? super.defaultIcon() : Icon.of(item);
    }

    protected Component itemName() {
        if (!filter.isBlank()) {
            return Component.translatable("corerpg_quests.filter.matching");
        }
        return item.isEmpty() ? Component.translatable("corerpg_quests.any_item") : item.getHoverName();
    }

    @Override
    public void addTooltip(List<Component> lines) {
        if (!filter.isBlank()) {
            lines.add(Component.literal(TextUtil.toSection("&7" + filter)).withStyle(ChatFormatting.GRAY));
        }
    }
}
