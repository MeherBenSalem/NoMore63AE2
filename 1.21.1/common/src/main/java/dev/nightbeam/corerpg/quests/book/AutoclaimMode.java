package dev.nightbeam.corerpg.quests.book;

import net.minecraft.util.StringRepresentable;

/**
 * Whether a reward is handed out automatically when its quest completes. NO_TOAST claims silently; INVISIBLE also
 * hides the reward in the quest book.
 */
public enum AutoclaimMode implements StringRepresentable {
    DEFAULT("default"),
    DISABLED("disabled"),
    ENABLED("enabled"),
    NO_TOAST("no_toast"),
    INVISIBLE("invisible");

    private final String name;

    AutoclaimMode(String name) {
        this.name = name;
    }

    public boolean claims() {
        return this == ENABLED || this == NO_TOAST || this == INVISIBLE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
