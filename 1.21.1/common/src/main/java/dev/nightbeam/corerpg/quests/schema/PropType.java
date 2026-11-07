package dev.nightbeam.corerpg.quests.schema;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A value type that a {@link Prop} can hold: how it is written to and read from JSON, copied and compared,
 * and how the editor shows it.
 */
public abstract class PropType<T> {
    private final EditorKind editorKind;

    protected PropType(EditorKind editorKind) {
        this.editorKind = editorKind;
    }

    public final EditorKind editorKind() {
        return editorKind;
    }

    public abstract JsonElement write(T value, JsonCtx ctx);

    /** Reads a value; throws {@link IllegalArgumentException} with a readable message on bad input. */
    public abstract T read(JsonElement json, JsonCtx ctx);

    public T copy(T value) {
        return value;
    }

    public boolean same(@Nullable T a, @Nullable T b) {
        return Objects.equals(a, b);
    }

    /** Returns an error message for an invalid value, or null when the value is acceptable. */
    public @Nullable String validate(T value) {
        return null;
    }
}
