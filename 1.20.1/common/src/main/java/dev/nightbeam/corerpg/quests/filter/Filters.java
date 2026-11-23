package dev.nightbeam.corerpg.quests.filter;

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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.nbt.CompoundTag;

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
            FoodProperties food = stack.getItem().getFoodProperties();
            return food != null && (nutrition == null || nutrition.test(food.getNutrition()));
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
            // Minecraft 1.20.1 edition: items carry NBT, not components. Common component ids map to their 1.20.1 NBT
            // home; anything else matches a top-level NBT key named like the id.
            CompoundTag tag = stack.getTag();
            if (tag == null) {
                return false;
            }
            String[] path = NBT_HOMES.get(id.toString());
            if (path != null && path.length == 0) {
                return !tag.isEmpty();
            }
            if (path != null) {
                CompoundTag at = tag;
                for (int i = 0; i < path.length - 1; i++) {
                    if (!at.contains(path[i], net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                        return false;
                    }
                    at = at.getCompound(path[i]);
                }
                return at.contains(path[path.length - 1]);
            }
            return tag.contains(id.getPath()) || tag.contains(id.toString());
        }

        private static final java.util.Map<String, String[]> NBT_HOMES = java.util.Map.of(
                "minecraft:custom_name", new String[]{"display", "Name"},
                "minecraft:lore", new String[]{"display", "Lore"},
                "minecraft:dyed_color", new String[]{"display", "color"},
                "minecraft:enchantments", new String[]{"Enchantments"},
                "minecraft:stored_enchantments", new String[]{"StoredEnchantments"},
                "minecraft:damage", new String[]{"Damage"},
                "minecraft:unbreakable", new String[]{"Unbreakable"},
                "minecraft:custom_model_data", new String[]{"CustomModelData"},
                "minecraft:custom_data", new String[]{});

        @Override
        public String toExpression() {
            return "component(" + id + ")";
        }
    }

    /** Has enchantments (on the item or stored, as on enchanted books); optionally a specific one at a level. */
    public record Enchanted(ResourceLocation enchantment, Comparison level) implements ItemFilter {
        @Override
        public boolean test(ItemStack stack) {
            // Enchantments on the item, or stored on an enchanted book.
            java.util.Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
            if (enchantments.isEmpty()) {
                return false;
            }
            if (enchantment == null) {
                return true;
            }
            for (java.util.Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                if (enchantment.equals(BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey())) && (level == null || level.test(entry.getValue()))) {
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
            return "rarity(" + rarity.name().toLowerCase(java.util.Locale.ROOT) + ")";
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
