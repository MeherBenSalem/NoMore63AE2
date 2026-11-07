package dev.nightbeam.corerpg.quests.filter;

import dev.nightbeam.corerpg.quests.McTest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterParserTest extends McTest {
    @Test
    void shorthands() {
        assertEquals(new Filters.ItemIs(ResourceLocation.parse("minecraft:stone")), FilterParser.parse("minecraft:stone"));
        assertEquals(new Filters.ItemIs(ResourceLocation.parse("minecraft:stone")), FilterParser.parse("stone"));
        assertEquals(new Filters.TagIs(ResourceLocation.parse("c:ingots")), FilterParser.parse("#c:ingots"));
        assertEquals(new Filters.ModIs("create"), FilterParser.parse("@create"));
        assertEquals(new Filters.ModIs("create"), FilterParser.parse("  @create  "));
    }

    @Test
    void namedForms() {
        assertEquals(new Filters.ItemIs(ResourceLocation.parse("minecraft:stone")), FilterParser.parse("item(minecraft:stone)"));
        assertEquals(new Filters.TagIs(ResourceLocation.parse("c:ingots")), FilterParser.parse("tag(c:ingots)"));
        assertEquals(new Filters.TagIs(ResourceLocation.parse("c:ingots")), FilterParser.parse("tag(#c:ingots)"));
        assertEquals(new Filters.ModIs("create"), FilterParser.parse("mod(create)"));
    }

    @Test
    void aliasesAndCase() {
        assertInstanceOf(Filters.Any.class, FilterParser.parse("or(a:b, c:d)"));
        assertInstanceOf(Filters.All.class, FilterParser.parse("AND(a:b, c:d)"));
        assertInstanceOf(Filters.OnlyOne.class, FilterParser.parse("xor(a:b, c:d)"));
        assertInstanceOf(Filters.OnlyOne.class, FilterParser.parse("only_one(a:b, c:d)"));
        assertInstanceOf(Filters.StackSize.class, FilterParser.parse("count(>=2)"));
        assertInstanceOf(Filters.HasComponent.class, FilterParser.parse("has(minecraft:custom_name)"));
        assertEquals(Filters.ALL, FilterParser.parse("true()"));
        assertEquals(Filters.NONE, FilterParser.parse("false()"));
    }

    @Test
    void nesting() {
        ItemFilter f = FilterParser.parse("any(#c:ingots, all(@create, not(block())), food(>=6))");
        Filters.Any any = assertInstanceOf(Filters.Any.class, f);
        assertEquals(3, any.children().size());
        Filters.All all = assertInstanceOf(Filters.All.class, any.children().get(1));
        assertInstanceOf(Filters.Not.class, all.children().get(1));
    }

    @Test
    void quotedStrings() {
        assertEquals(new Filters.Name("Ancient, (old) \"blade\"", false), FilterParser.parse("name(\"Ancient, (old) \\\"blade\\\"\")"));
        assertEquals(new Filters.Name("it's", false), FilterParser.parse("name('it\\'s')"));
        // A bare string is a name filter.
        assertEquals(new Filters.Name("Rune", false), FilterParser.parse("\"Rune\""));
    }

    @Test
    void comparisons() {
        assertEquals(new Filters.Food(Comparison.parse(">=6", 0)), FilterParser.parse("food(>= 6)"));
        assertEquals(new Filters.Food(null), FilterParser.parse("food()"));
        assertEquals(new Filters.Durability(Comparison.parse("<25%", 0)), FilterParser.parse("durability(<25%)"));
        assertEquals(new Filters.StackSize(Comparison.parse("2..5", 0), false), FilterParser.parse("stack_size(2..5)"));
        assertEquals(new Filters.StackSize(Comparison.parse("=1", 0), true), FilterParser.parse("max_stack(1)"));
        assertEquals(new Filters.Enchanted(ResourceLocation.parse("minecraft:sharpness"), Comparison.parse(">=3", 0)),
                FilterParser.parse("enchant(minecraft:sharpness, >=3)"));
        assertEquals(new Filters.Enchanted(ResourceLocation.parse("minecraft:mending"), null), FilterParser.parse("enchant(minecraft:mending)"));
        assertEquals(new Filters.RarityIs(Rarity.EPIC), FilterParser.parse("rarity(EPIC)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "minecraft:diamond", "#c:ingots", "@create",
            "any(minecraft:stone, #c:gems)", "all(@create, block())", "one(#c:ores, @create)", "not(block())",
            "block()", "food()", "food(>=6)", "durability(<25%)", "durability(10..20)", "stack_size(>=16)", "max_stack(=1)",
            "name(\"Ancient\")", "name_regex(\"^Rune.*\")", "component(minecraft:custom_name)", "enchanted()",
            "enchant(minecraft:sharpness, >=3)", "enchant(minecraft:mending)", "rarity(epic)", "custom(my_mod:relics)",
            "always()", "never()",
            "any(all(not(@minecraft), food(2..4)), one(#c:ingots, name(\"x, y\")))"
    })
    void canonicalRoundTrip(String expression) {
        ItemFilter parsed = FilterParser.parse(expression);
        String canonical = parsed.toExpression();
        ItemFilter reparsed = FilterParser.parse(canonical);
        assertEquals(parsed, reparsed, expression + " -> " + canonical);
        assertEquals(canonical, reparsed.toExpression());
    }

    @Test
    void everyRegisteredTypeHasAWorkingExample() {
        for (FilterTypes.Type type : FilterTypes.all()) {
            assertNull(FilterParser.validate(type.example()), type.name() + ": " + type.example());
            ItemFilter f = FilterParser.parse(type.example());
            assertEquals(f, FilterParser.parse(f.toExpression()), type.name());
        }
        List<String> names = FilterTypes.all().stream().map(FilterTypes.Type::name).toList();
        assertTrue(names.containsAll(List.of("item", "tag", "mod", "any", "all", "one", "not", "block", "food", "durability",
                "stack_size", "max_stack", "name", "name_regex", "component", "enchanted", "enchant", "rarity", "custom")));
    }

    @Test
    void errorsHavePositionsAndMessages() {
        assertError("", 0, "empty");
        assertError("   ", 0, "empty");
        assertError("bogus(minecraft:stone)", 0, "Unknown filter");
        assertError("any(minecraft:stone", 0, "Missing ')'");
        assertError("minecraft:stone minecraft:dirt", -1, null);
        assertError("any(a:b c:d)", -1, null);
        assertError("any()", 0, "at least one");
        assertError("not(a:b, c:d)", 0, "exactly one");
        assertError("block(a:b)", 0, "no arguments");
        assertError("food(lots)", 5, "comparison");
        assertError("rarity(mythic)", 0, "Unknown rarity");
        assertError("name_regex(\"[\")", 0, "Invalid pattern");
        assertError("name(\"unclosed)", 5, "Unclosed string");
        assertError("any(a:b, ,c:d)", 9, "Expected a filter");
        assertError("@Bad Mod", 0, "Invalid mod id");
        assertError("Not An Id", 0, "Invalid id");
        assertError("item(any(a:b))", 5, "Expected a value");
        assertError("minecraft:stone)", 15, "Unexpected");
    }

    private static void assertError(String expression, int position, String messagePart) {
        FilterException e = assertThrows(FilterException.class, () -> FilterParser.parse(expression), expression);
        if (position >= 0) {
            assertEquals(position, e.position(), expression + ": " + e.getMessage());
        }
        if (messagePart != null) {
            assertTrue(e.getMessage().contains(messagePart), expression + ": " + e.getMessage());
        }
        assertNotNull(FilterParser.validate(expression));
    }

    @Test
    void cacheReturnsNoneForBrokenFilters() {
        assertEquals(Filters.NONE, FilterCache.get("any("));
        ItemFilter ok = FilterCache.get("minecraft:apple");
        assertTrue(ok == FilterCache.get("minecraft:apple"));
    }
}
