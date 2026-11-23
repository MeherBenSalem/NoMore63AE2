package dev.nightbeam.corerpg.quests.task;

import dev.nightbeam.corerpg.quests.filter.FilterCache;
import dev.nightbeam.corerpg.quests.filter.ItemFilter;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;


/** Matches item stacks against a required item, a smart filter item or a filter expression. */
public final class ItemMatcher {
    private ItemMatcher() {
    }

    /**
     * @param required the item the task names (may be a smart filter item, or empty for "anything")
     * @param filter   a filter expression; when not blank it wins over {@code required}
     */
    public static boolean matches(ItemStack required, String filter, ComponentMatch mode, ItemStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (filter != null && !filter.isBlank()) {
            return FilterCache.get(filter).test(candidate);
        }
        if (required.isEmpty()) {
            return true;
        }
        ItemFilter smart = smartFilter(required);
        if (smart != null) {
            return smart.test(candidate);
        }
        if (!candidate.is(required.getItem())) {
            return false;
        }
        return switch (mode) {
            case IGNORE -> true;
            case STRICT -> ItemStack.isSameItemSameTags(required, candidate);
            case FUZZY -> fuzzy(required, candidate);
        };
    }

    /** True when the stack is a configured smart filter item. */
    public static ItemFilter smartFilter(ItemStack stack) {
        if (ModRegistry.SMART_FILTER == null || !stack.is(ModRegistry.SMART_FILTER.get())) {
            return null;
        }
        String expression = dev.nightbeam.corerpg.quests.util.StackTags.getString(stack, ModRegistry.FILTER_TAG);
        return expression == null || expression.isBlank() ? null : FilterCache.get(expression);
    }

    private static boolean fuzzy(ItemStack required, ItemStack candidate) {
        // Minecraft 1.20.1 edition: every NBT value the required stack sets must be present on the candidate.
        return NbtUtils.compareNbt(required.getTag(), candidate.getTag(), true);
    }
}
