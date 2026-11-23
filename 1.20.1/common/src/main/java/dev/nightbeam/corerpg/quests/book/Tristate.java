package dev.nightbeam.corerpg.quests.book;

import net.minecraft.util.StringRepresentable;

/** A yes/no override that can also defer to the chapter or book default. */
public enum Tristate implements StringRepresentable {
    DEFAULT("default"),
    TRUE("true"),
    FALSE("false");

    private final String name;

    Tristate(String name) {
        this.name = name;
    }

    public boolean get(boolean fallback) {
        return this == DEFAULT ? fallback : this == TRUE;
    }

    public static Tristate of(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
