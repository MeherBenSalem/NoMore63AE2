package dev.nightbeam.corerpg.quests.schema;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One editable, serialisable field of a quest object. The same definition drives the JSON files, the network sync
 * and the generated editor screen, so they can never disagree about a field.
 */
public final class Prop<T> {
    private final String key;
    private final PropType<T> type;
    private final T defaultValue;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final String category;
    private boolean editorHidden;

    Prop(String key, PropType<T> type, T defaultValue, Supplier<T> getter, Consumer<T> setter, String category) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.getter = getter;
        this.setter = setter;
        this.category = category;
    }

    public String key() {
        return key;
    }

    public PropType<T> type() {
        return type;
    }

    public T defaultValue() {
        return type.copy(defaultValue);
    }

    public String category() {
        return category;
    }

    public boolean editorHidden() {
        return editorHidden;
    }

    /** Hides the property from the generated editor (it is still saved and synced). */
    public Prop<T> hideInEditor() {
        editorHidden = true;
        return this;
    }

    public T get() {
        return getter.get();
    }

    /** Stores a copy, so callers may pass immutable lists and the object can still edit its own values later. */
    public void set(T value) {
        setter.accept(value == null ? null : type.copy(value));
    }

    public boolean isDefault() {
        return type.same(get(), defaultValue);
    }

    public void writeTo(JsonObject json, JsonCtx ctx) {
        if (!isDefault()) {
            json.add(key, type.write(get(), ctx));
        }
    }

    /** Reads this property, falling back to the default (with a warning) when the value is missing or invalid. */
    public void readFrom(JsonObject json, JsonCtx ctx, String owner) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            set(defaultValue());
            return;
        }
        try {
            set(type.read(json.get(key), ctx));
        } catch (RuntimeException e) {
            CoreQuests.LOG.warn("{}: invalid value for '{}' ({}); using the default", owner, key, e.getMessage());
            set(defaultValue());
        }
    }
}
