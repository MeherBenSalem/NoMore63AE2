package dev.nightbeam.corerpg.quests.schema;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.task.Ref;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The ordered list of properties an object declares, grouped into editor categories. */
public final class Props implements Iterable<Prop<?>> {
    private final List<Prop<?>> props = new ArrayList<>();
    private String category = "general";

    public Props category(String category) {
        this.category = category;
        return this;
    }

    public <T> Prop<T> add(String key, PropType<T> type, T defaultValue, Supplier<T> getter, Consumer<T> setter) {
        for (Prop<?> p : props) {
            if (p.key().equals(key)) {
                throw new IllegalStateException("Duplicate property " + key);
            }
        }
        Prop<T> prop = new Prop<>(key, type, defaultValue, getter, setter, category);
        props.add(prop);
        return prop;
    }

    public Prop<String> string(String key, String def, Supplier<String> getter, Consumer<String> setter) {
        return add(key, PropTypes.STRING, def, getter, setter);
    }

    public Prop<List<String>> text(String key, Supplier<List<String>> getter, Consumer<List<String>> setter) {
        return add(key, PropTypes.TEXT, List.of(), getter, setter);
    }

    public Prop<List<String>> stringList(String key, Supplier<List<String>> getter, Consumer<List<String>> setter) {
        return add(key, PropTypes.STRING_LIST, List.of(), getter, setter);
    }

    public Prop<List<String>> idList(String key, Supplier<List<String>> getter, Consumer<List<String>> setter) {
        return add(key, PropTypes.ID_LIST, List.of(), getter, setter);
    }

    public Prop<String> id(String key, Supplier<String> getter, Consumer<String> setter) {
        return add(key, PropTypes.ID, "", getter, setter);
    }

    public Prop<Boolean> bool(String key, boolean def, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return add(key, PropTypes.BOOL, def, getter, setter);
    }

    public Prop<Integer> integer(String key, int def, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter) {
        return add(key, PropTypes.intRange(min, max), def, getter, setter);
    }

    public Prop<Long> longValue(String key, long def, long min, long max, Supplier<Long> getter, Consumer<Long> setter) {
        return add(key, PropTypes.longRange(min, max), def, getter, setter);
    }

    public Prop<Double> decimal(String key, double def, double min, double max, Supplier<Double> getter, Consumer<Double> setter) {
        return add(key, PropTypes.doubleRange(min, max), def, getter, setter);
    }

    public Prop<Integer> color(String key, int def, Supplier<Integer> getter, Consumer<Integer> setter) {
        return add(key, PropTypes.COLOR, def, getter, setter);
    }

    public <E extends Enum<E> & StringRepresentable> Prop<E> enumValue(String key, Class<E> clazz, E def, Supplier<E> getter, Consumer<E> setter) {
        return add(key, PropTypes.enumType(clazz), def, getter, setter);
    }

    public Prop<ResourceLocation> resource(String key, ResourceKind kind, @Nullable ResourceLocation def, Supplier<ResourceLocation> getter, Consumer<ResourceLocation> setter) {
        return add(key, PropTypes.resource(kind), def, getter, setter);
    }

    public Prop<Ref> ref(String key, ResourceKind kind, Supplier<Ref> getter, Consumer<Ref> setter) {
        return add(key, PropTypes.ref(kind), null, getter, setter);
    }

    public Prop<ItemStack> item(String key, Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        return add(key, PropTypes.ITEM_STACK, ItemStack.EMPTY, getter, setter);
    }

    public Prop<List<ItemStack>> items(String key, Supplier<List<ItemStack>> getter, Consumer<List<ItemStack>> setter) {
        return add(key, PropTypes.ITEM_LIST, List.of(), getter, setter);
    }

    public Prop<Icon> icon(String key, Supplier<Icon> getter, Consumer<Icon> setter) {
        return add(key, PropTypes.ICON, Icon.EMPTY, getter, setter);
    }

    public Prop<String> filter(String key, Supplier<String> getter, Consumer<String> setter) {
        return add(key, PropTypes.FILTER, "", getter, setter);
    }

    public @Nullable Prop<?> get(String key) {
        for (Prop<?> p : props) {
            if (p.key().equals(key)) {
                return p;
            }
        }
        return null;
    }

    public List<Prop<?>> list() {
        return Collections.unmodifiableList(props);
    }

    public Set<String> categories() {
        Set<String> set = new LinkedHashSet<>();
        props.forEach(p -> set.add(p.category()));
        return set;
    }

    public void writeTo(JsonObject json, JsonCtx ctx) {
        for (Prop<?> p : props) {
            p.writeTo(json, ctx);
        }
    }

    public void readFrom(JsonObject json, JsonCtx ctx, String owner) {
        for (Prop<?> p : props) {
            p.readFrom(json, ctx, owner);
        }
    }

    @Override
    public Iterator<Prop<?>> iterator() {
        return list().iterator();
    }
}
