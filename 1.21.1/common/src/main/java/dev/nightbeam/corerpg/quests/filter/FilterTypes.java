package dev.nightbeam.corerpg.quests.filter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** The filter functions and how their arguments are read. Also the registry for {@code custom(key)} predicates. */
public final class FilterTypes {
    /** A filter function, documented for the in-game filter editor. */
    public record Type(String name, List<String> aliases, String signature, String example, Factory factory) {
    }

    @FunctionalInterface
    public interface Factory {
        ItemFilter create(List<FilterParser.Arg> args, int position);
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();
    private static final Map<String, Type> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, Predicate<ItemStack>> CUSTOM = new ConcurrentHashMap<>();

    static {
        register("item", List.of(), "item(id)", "minecraft:diamond", (a, p) -> new Filters.ItemIs(id(single(a, p, "item"))));
        register("tag", List.of(), "tag(id)", "#c:ingots", (a, p) -> new Filters.TagIs(id(single(a, p, "tag"))));
        register("mod", List.of(), "mod(name)", "@create", (a, p) -> new Filters.ModIs(text(single(a, p, "mod"))));
        register("any", List.of("or"), "any(filter, ...)", "any(#c:ingots, #c:gems)", (a, p) -> new Filters.Any(children(a, p, "any")));
        register("all", List.of("and"), "all(filter, ...)", "all(@create, block())", (a, p) -> new Filters.All(children(a, p, "all")));
        register("one", List.of("only_one", "xor"), "one(filter, ...)", "one(#c:ores, @create)", (a, p) -> new Filters.OnlyOne(children(a, p, "one")));
        register("not", List.of(), "not(filter)", "not(block())", (a, p) -> new Filters.Not(toFilter(single(a, p, "not"))));
        register("block", List.of(), "block()", "block()", (a, p) -> noArgs(a, p, "block", new Filters.IsBlock()));
        register("food", List.of(), "food([nutrition])", "food(>=6)", (a, p) -> new Filters.Food(a.isEmpty() ? null : comparison(single(a, p, "food"))));
        register("durability", List.of(), "durability(remaining)", "durability(<25%)", (a, p) -> new Filters.Durability(comparison(single(a, p, "durability"))));
        register("stack_size", List.of("count"), "stack_size(count)", "stack_size(>=16)", (a, p) -> new Filters.StackSize(comparison(single(a, p, "stack_size")), false));
        register("max_stack", List.of(), "max_stack(count)", "max_stack(=1)", (a, p) -> new Filters.StackSize(comparison(single(a, p, "max_stack")), true));
        register("name", List.of(), "name(\"text\")", "name(\"Ancient\")", (a, p) -> new Filters.Name(text(single(a, p, "name")), false));
        register("name_regex", List.of(), "name_regex(\"pattern\")", "name_regex(\"^Rune.*\")", (a, p) -> {
            String pattern = text(single(a, p, "name_regex"));
            try {
                java.util.regex.Pattern.compile(pattern);
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new FilterException("Invalid pattern: " + e.getDescription(), p);
            }
            return new Filters.Name(pattern, true);
        });
        register("component", List.of("has"), "component(id)", "component(minecraft:custom_name)", (a, p) -> new Filters.HasComponent(id(single(a, p, "component"))));
        register("enchanted", List.of(), "enchanted()", "enchanted()", (a, p) -> noArgs(a, p, "enchanted", new Filters.Enchanted(null, null)));
        register("enchant", List.of(), "enchant(id[, level])", "enchant(minecraft:sharpness, >=3)", (a, p) -> {
            if (a.isEmpty() || a.size() > 2) {
                throw new FilterException("enchant() takes an enchantment id and an optional level", p);
            }
            return new Filters.Enchanted(id(a.get(0)), a.size() == 2 ? comparison(a.get(1)) : null);
        });
        register("rarity", List.of(), "rarity(name)", "rarity(epic)", (a, p) -> {
            String name = text(single(a, p, "rarity")).toLowerCase(Locale.ROOT);
            for (Rarity r : Rarity.values()) {
                if (r.getSerializedName().equals(name)) {
                    return new Filters.RarityIs(r);
                }
            }
            throw new FilterException("Unknown rarity '" + name + "' (common, uncommon, rare, epic)", p);
        });
        register("custom", List.of(), "custom(key)", "custom(my_mod:relics)", (a, p) -> new Filters.Custom(text(single(a, p, "custom"))));
        register("always", List.of("true"), "always()", "always()", (a, p) -> noArgs(a, p, "always", Filters.ALL));
        register("never", List.of("false"), "never()", "never()", (a, p) -> noArgs(a, p, "never", Filters.NONE));
    }

    private FilterTypes() {
    }

    public static void bootstrap() {
    }

    private static void register(String name, List<String> aliases, String signature, String example, Factory factory) {
        Type type = new Type(name, aliases, signature, example, factory);
        TYPES.put(name, type);
        BY_NAME.put(name, type);
        aliases.forEach(alias -> BY_NAME.put(alias, type));
    }

    public static Collection<Type> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /** Registers a predicate usable as {@code custom(key)} in any filter. */
    public static void registerCustom(String key, Predicate<ItemStack> predicate) {
        CUSTOM.put(key, predicate);
        FilterCache.clear();
    }

    public static @Nullable Predicate<ItemStack> customPredicate(String key) {
        return CUSTOM.get(key);
    }

    /** Interprets a parsed argument as a filter: a call, or an id/tag/mod shorthand. */
    public static ItemFilter toFilter(FilterParser.Arg arg) {
        return switch (arg) {
            case FilterParser.Arg.Call call -> {
                Type type = BY_NAME.get(call.name().toLowerCase(Locale.ROOT));
                if (type == null) {
                    throw new FilterException("Unknown filter '" + call.name() + "'", call.position());
                }
                yield type.factory().create(call.args(), call.position());
            }
            case FilterParser.Arg.Atom atom -> shorthand(atom.text(), atom.position());
            case FilterParser.Arg.Str str -> new Filters.Name(str.text(), false);
        };
    }

    private static ItemFilter shorthand(String text, int position) {
        if (text.startsWith("#")) {
            return new Filters.TagIs(parseId(text.substring(1), position));
        }
        if (text.startsWith("@")) {
            String mod = text.substring(1).trim();
            if (!mod.matches("[a-z0-9_.-]+")) {
                throw new FilterException("Invalid mod id '" + mod + "'", position);
            }
            return new Filters.ModIs(mod);
        }
        return new Filters.ItemIs(parseId(text, position));
    }

    private static ResourceLocation parseId(String text, int position) {
        ResourceLocation id = ResourceLocation.tryParse(text.trim());
        if (id == null) {
            throw new FilterException("Invalid id '" + text.trim() + "'", position);
        }
        return id;
    }

    private static FilterParser.Arg single(List<FilterParser.Arg> args, int position, String name) {
        if (args.size() != 1) {
            throw new FilterException(name + "() takes exactly one argument", position);
        }
        return args.getFirst();
    }

    private static ItemFilter noArgs(List<FilterParser.Arg> args, int position, String name, ItemFilter filter) {
        if (!args.isEmpty()) {
            throw new FilterException(name + "() takes no arguments", position);
        }
        return filter;
    }

    private static List<ItemFilter> children(List<FilterParser.Arg> args, int position, String name) {
        if (args.isEmpty()) {
            throw new FilterException(name + "() needs at least one filter", position);
        }
        List<ItemFilter> list = new ArrayList<>(args.size());
        args.forEach(arg -> list.add(toFilter(arg)));
        return List.copyOf(list);
    }

    private static ResourceLocation id(FilterParser.Arg arg) {
        String text = text(arg);
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        return parseId(text, arg.position());
    }

    private static String text(FilterParser.Arg arg) {
        return switch (arg) {
            case FilterParser.Arg.Atom atom -> atom.text();
            case FilterParser.Arg.Str str -> str.text();
            case FilterParser.Arg.Call call -> throw new FilterException("Expected a value, not " + call.name() + "(...)", call.position());
        };
    }

    private static Comparison comparison(FilterParser.Arg arg) {
        return Comparison.parse(text(arg), arg.position());
    }
}
