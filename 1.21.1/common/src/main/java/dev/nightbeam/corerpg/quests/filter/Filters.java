package dev.nightbeam.corerpg.quests.filter;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The built-in filters. */
public final class Filters {
    private Filters() {
    }

    public static final ItemFilter ALL = new Simple("always", s -> !s.isEmpty());
    public static final ItemFilter NONE = new Simple("never", s -> false);

    record Simple(String name, Predicate<ItemStack> predicate) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return predicate.test(stack);
        }

        @Override
        public String toExpression() {
            return name + "()";
        }
    }

    public record ItemIs(ResourceLocation id) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
        }

        @Override
        public String toExpression() {
            return id.toString();
        }
    }

    public record TagIs(ResourceLocation id) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return stack.is(TagKey.create(Registries.ITEM, id));
        }

        @Override
        public String toExpression() {
            return "#" + id;
        }
    }

    public record ModIs(String mod) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(mod);
        }

        @Override
        public String toExpression() {
            return "@" + mod;
        }
    }

    public record Any(List<ItemFilter> children) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            for (ItemFilter f : children) {
                if (f.test(stack)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toExpression() {
            return "any(" + children.stream().map(ItemFilter::toExpression).collect(Collectors.joining(", ")) + ")";
        }
    }

    public record All(List<ItemFilter> children) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            for (ItemFilter f : children) {
                if (!f.test(stack)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toExpression() {
            return "all(" + children.stream().map(ItemFilter::toExpression).collect(Collectors.joining(", ")) + ")";
        }
    }

    public record OnlyOne(List<ItemFilter> children) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            int matches = 0;
            for (ItemFilter f : children) {
                if (f.test(stack) && ++matches > 1) {
                    return false;
                }
            }
            return matches == 1;
        }

        @Override
        public String toExpression() {
            return "one(" + children.stream().map(ItemFilter::toExpression).collect(Collectors.joining(", ")) + ")";
        }
    }

    public record Not(ItemFilter child) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return !stack.isEmpty() && !child.test(stack);
        }

        @Override
        public String toExpression() {
            return "not(" + child.toExpression() + ")";
        }
    }

    public record IsBlock() implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return stack.getItem() instanceof BlockItem;
        }

        @Override
        public String toExpression() {
            return "block()";
        }
    }

    public record Food(Comparison nutrition) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            return food != null && (nutrition == null || nutrition.test(food.nutrition()));
        }

        @Override
        public String toExpression() {
            return nutrition == null ? "food()" : "food(" + nutrition + ")";
        }
    }

    /** Remaining durability, in points or (with %) percent. Non-damageable items never match. */
    public record Durability(Comparison remaining) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            if (!stack.isDamageableItem()) {
                return false;
            }
            int max = stack.getMaxDamage();
            int left = max - stack.getDamageValue();
            return remaining.test(remaining.percent() ? left * 100.0 / max : left);
        }

        @Override
        public String toExpression() {
            return "durability(" + remaining + ")";
        }
    }

    public record StackSize(Comparison count, boolean max) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return !stack.isEmpty() && count.test(max ? stack.getMaxStackSize() : stack.getCount());
        }

        @Override
        public String toExpression() {
            return (max ? "max_stack(" : "stack_size(") + count + ")";
        }
    }

    public record Name(String text, boolean regex) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            String name = stack.getHoverName().getString();
            if (regex) {
                return Pattern.compile(text, Pattern.CASE_INSENSITIVE).matcher(name).find();
            }
            return name.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
        }

        @Override
        public String toExpression() {
            return (regex ? "name_regex(" : "name(") + FilterParser.quote(text) + ")";
        }
    }

    public record HasComponent(ResourceLocation id) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
            return type != null && stack.has(type);
        }

        @Override
        public String toExpression() {
            return "component(" + id + ")";
        }
    }

    /** Has enchantments (on the item or stored, as on enchanted books); optionally a specific one at a level. */
    public record Enchanted(ResourceLocation enchantment, Comparison level) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return check(stack.get(DataComponents.ENCHANTMENTS)) || check(stack.get(DataComponents.STORED_ENCHANTMENTS));
        }

        private boolean check(ItemEnchantments enchantments) {
            if (enchantments == null || enchantments.isEmpty()) {
                return false;
            }
            if (enchantment == null) {
                return true;
            }
            for (var entry : enchantments.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                if (holder.is(enchantment) && (level == null || level.test(entry.getIntValue()))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toExpression() {
            if (enchantment == null) {
                return "enchanted()";
            }
            return "enchant(" + enchantment + (level == null ? "" : ", " + level) + ")";
        }
    }

    public record RarityIs(Rarity rarity) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            return !stack.isEmpty() && stack.getRarity() == rarity;
        }

        @Override
        public String toExpression() {
            return "rarity(" + rarity.getSerializedName() + ")";
        }
    }

    public record Custom(String key) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            Predicate<ItemStack> predicate = FilterTypes.customPredicate(key);
            return predicate != null && predicate.test(stack);
        }

        @Override
        public String toExpression() {
            return "custom(" + key + ")";
        }
    }

    static Item itemOrNull(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }
}
