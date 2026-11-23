package dev.nightbeam.corerpg.quests.task;

import dev.nightbeam.corerpg.quests.filter.FilterCache;
import dev.nightbeam.corerpg.quests.filter.ItemFilter;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
            case STRICT -> ItemStack.isSameItemSameComponents(required, candidate);
            case FUZZY -> fuzzy(required, candidate);
        };
    }

    /** True when the stack is a configured smart filter item. */
    public static ItemFilter smartFilter(ItemStack stack) {
        if (ModRegistry.SMART_FILTER == null || !stack.is(ModRegistry.SMART_FILTER.get())) {
            return null;
        }
        String expression = stack.get(ModRegistry.FILTER_COMPONENT.get());
        return expression == null || expression.isBlank() ? null : FilterCache.get(expression);
    }

    private static boolean fuzzy(ItemStack required, ItemStack candidate) {
        DataComponentPatch patch = required.getComponentsPatch();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            Object wanted = entry.getValue().orElse(null);
            Object actual = candidate.get(entry.getKey());
            if (!Objects.equals(wanted, actual)) {
                return false;
            }
        }
        return true;
    }
}
