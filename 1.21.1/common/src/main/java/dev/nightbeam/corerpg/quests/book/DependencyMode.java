package dev.nightbeam.corerpg.quests.book;

import net.minecraft.util.StringRepresentable;

/** What the dependencies of a quest must have reached before the quest unlocks. */
public enum DependencyMode implements StringRepresentable {
    ALL_COMPLETED("all_completed"),
    ONE_COMPLETED("one_completed"),
    ALL_STARTED("all_started"),
    ONE_STARTED("one_started");

    private final String name;

    DependencyMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
