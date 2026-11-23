package dev.nightbeam.corerpg.quests.client.screen.filter;

import dev.nightbeam.corerpg.quests.filter.ItemFilter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The items a filter matches: the player's inventory first (so damaged, enchanted and named items show up), then the
 * default stack of every registered item. Counts every match but keeps at most {@link #LIMIT} for display.
 */
public final class FilterPreview {
    public static final int LIMIT = 200;
    private static List<ItemStack> defaults;

    private final List<ItemStack> shown = new ArrayList<>();
    private int total;
    private int fromInventory;

    public List<ItemStack> shown() {
        return Collections.unmodifiableList(shown);
    }

    /** Every match, including the ones not shown. */
    public int total() {
        return total;
    }

    /** How many of the shown stacks came from the player's inventory. */
    public int fromInventory() {
        return fromInventory;
    }

    public void update(@Nullable ItemFilter filter, @Nullable Player player) {
        shown.clear();
        total = 0;
        fromInventory = 0;
        if (filter == null) {
            return;
        }
        if (player != null) {
            Inventory inventory = player.getInventory();
            List<ItemStack> carried = new ArrayList<>(inventory.items);
            carried.addAll(inventory.armor);
            carried.addAll(inventory.offhand);
            for (ItemStack stack : carried) {
                if (!stack.isEmpty() && test(filter, stack) && !containsSame(stack, shown.size())) {
                    total++;
                    if (shown.size() < LIMIT) {
                        shown.add(stack.copyWithCount(1));
                        fromInventory++;
                    }
                }
            }
        }
        for (ItemStack stack : defaults()) {
            // Registry stacks are all different items, so only the inventory entries can be duplicates.
            if (test(filter, stack) && !containsSame(stack, fromInventory)) {
                total++;
                if (shown.size() < LIMIT) {
                    shown.add(stack);
                }
            }
        }
    }

    private boolean containsSame(ItemStack stack, int limit) {
        for (int i = 0; i < Math.min(limit, shown.size()); i++) {
            if (ItemStack.isSameItemSameComponents(shown.get(i), stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean test(ItemFilter filter, ItemStack stack) {
        try {
            return filter.test(stack);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<ItemStack> defaults() {
        if (defaults == null) {
            List<ItemStack> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR) {
                    try {
                        ItemStack stack = item.getDefaultInstance();
                        if (!stack.isEmpty()) {
                            list.add(stack);
                        }
                    } catch (RuntimeException ignored) {
                        // An item whose default stack cannot be built is simply not previewed.
                    }
                }
            }
            defaults = List.copyOf(list);
        }
        return defaults;
    }
}
