package dev.nightbeam.corerpg.quests.task;

import net.minecraft.util.StringRepresentable;

/**
 * How an item's data components are compared. IGNORE: any stack of the item. FUZZY: every component set on the
 * required stack must match (extra components such as damage are fine). STRICT: components must be identical.
 */
public enum ComponentMatch implements StringRepresentable {
    IGNORE("ignore"),
    FUZZY("fuzzy"),
    STRICT("strict");

    private final String name;

    ComponentMatch(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
