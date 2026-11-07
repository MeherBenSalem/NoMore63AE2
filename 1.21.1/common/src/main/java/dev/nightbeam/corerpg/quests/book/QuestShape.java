package dev.nightbeam.corerpg.quests.book;

import net.minecraft.util.StringRepresentable;

/** The outline drawn behind a quest icon in the quest book. */
public enum QuestShape implements StringRepresentable {
    DEFAULT("default"),
    CIRCLE("circle"),
    SQUARE("square"),
    ROUNDED_SQUARE("rsquare"),
    DIAMOND("diamond"),
    PENTAGON("pentagon"),
    HEXAGON("hexagon"),
    OCTAGON("octagon"),
    HEART("heart"),
    GEAR("gear"),
    SHIELD("shield"),
    STAR("star"),
    NONE("none");

    private final String name;

    QuestShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The texture name under textures/gui/shape/. */
    public String texture() {
        return name;
    }
}
