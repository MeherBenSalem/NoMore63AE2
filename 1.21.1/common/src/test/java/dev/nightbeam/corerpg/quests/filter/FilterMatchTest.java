package dev.nightbeam.corerpg.quests.filter;

import dev.nightbeam.corerpg.quests.McTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterMatchTest extends McTest {
    private static boolean matches(String filter, ItemStack stack) {
        return FilterParser.parse(filter).test(stack);
    }

    @Test
    void itemAndMod() {
        assertTrue(matches("minecraft:diamond", new ItemStack(Items.DIAMOND)));
        assertFalse(matches("minecraft:diamond", new ItemStack(Items.EMERALD)));
        assertTrue(matches("@minecraft", new ItemStack(Items.EMERALD)));
        assertFalse(matches("@create", new ItemStack(Items.EMERALD)));
        assertFalse(matches("@minecraft", ItemStack.EMPTY));
    }

    @Test
    void logic() {
        ItemStack apple = new ItemStack(Items.APPLE);
        assertTrue(matches("any(minecraft:carrot, minecraft:apple)", apple));
        assertFalse(matches("all(minecraft:apple, block())", apple));
        assertTrue(matches("not(block())", apple));
        assertFalse(matches("not(block())", ItemStack.EMPTY));
        assertTrue(matches("one(minecraft:apple, block())", apple));
        assertFalse(matches("one(minecraft:apple, food())", apple));
        assertTrue(matches("always()", apple));
        assertFalse(matches("never()", apple));
        assertFalse(matches("always()", ItemStack.EMPTY));
    }

    @Test
    void blockAndFood() {
        assertTrue(matches("block()", new ItemStack(Items.STONE)));
        assertFalse(matches("block()", new ItemStack(Items.STICK)));
        assertTrue(matches("food()", new ItemStack(Items.BREAD)));
        assertTrue(matches("food(>=5)", new ItemStack(Items.BREAD)));
        assertFalse(matches("food(>=6)", new ItemStack(Items.BREAD)));
        assertFalse(matches("food()", new ItemStack(Items.STONE)));
    }

    @Test
    void durabilityAndStacks() {
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        int max = pick.getMaxDamage();
        pick.setDamageValue(max - 10);
        assertTrue(matches("durability(<=10)", pick));
        assertTrue(matches("durability(<5%)", pick));
        assertFalse(matches("durability(>50%)", pick));
        assertFalse(matches("durability(>0)", new ItemStack(Items.STONE)));
        assertTrue(matches("stack_size(>=16)", new ItemStack(Items.STONE, 32)));
        assertFalse(matches("stack_size(>=16)", new ItemStack(Items.STONE, 3)));
        assertTrue(matches("max_stack(=1)", pick));
        assertTrue(matches("max_stack(=64)", new ItemStack(Items.STONE)));
    }

    @Test
    void nameAndComponents() {
        ItemStack named = new ItemStack(Items.IRON_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Ancient Runeblade"));
        assertTrue(matches("name(\"runeblade\")", named));
        assertTrue(matches("name_regex(\"^ancient\")", named));
        assertFalse(matches("name_regex(\"^rune\")", named));
        assertTrue(matches("component(minecraft:custom_name)", named));
        assertFalse(matches("component(minecraft:custom_name)", new ItemStack(Items.IRON_SWORD)));
        assertFalse(matches("component(minecraft:not_a_component)", named));
        assertTrue(matches("rarity(common)", new ItemStack(Items.STONE)));
        assertTrue(matches("rarity(epic)", new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
        assertFalse(matches("enchanted()", new ItemStack(Items.IRON_SWORD)));
    }

    @Test
    void customPredicates() {
        assertFalse(matches("custom(test:sticks)", new ItemStack(Items.STICK)));
        FilterTypes.registerCustom("test:sticks", s -> s.is(Items.STICK));
        assertTrue(matches("custom(test:sticks)", new ItemStack(Items.STICK)));
        assertFalse(matches("custom(test:sticks)", new ItemStack(Items.STONE)));
    }
}
