package dev.nightbeam.corerpg.quests.filter;

import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** A parsed filter expression. {@link #toExpression()} gives back a canonical expression that parses to the same filter. */
public interface ItemFilter extends Predicate<ItemStack> {
    @Override
    boolean test(ItemStack stack);

    String toExpression();
}
