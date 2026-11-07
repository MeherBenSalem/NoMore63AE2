package dev.nightbeam.corerpg.quests.book;

import net.minecraft.util.StringRepresentable;

/**
 * LINEAR: a quest's tasks only count once its dependencies are done. FLEXIBLE: tasks count any time, and the quest
 * completes as soon as its dependencies are done too.
 */
public enum ProgressionMode implements StringRepresentable {
    DEFAULT("default"),
    LINEAR("linear"),
    FLEXIBLE("flexible");

    private final String name;

    ProgressionMode(String name) {
        this.name = name;
    }

    public ProgressionMode or(ProgressionMode fallback) {
        return this == DEFAULT ? fallback : this;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
