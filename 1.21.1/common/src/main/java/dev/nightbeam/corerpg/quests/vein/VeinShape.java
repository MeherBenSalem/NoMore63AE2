package dev.nightbeam.corerpg.quests.vein;

import net.minecraft.network.chat.Component;

/** The patterns vein mining can break. */
public enum VeinShape {
    /** Every connected block of the same kind (ore veins, trees). */
    SHAPELESS("shapeless"),
    /** A 1x1 tunnel straight ahead. */
    SMALL_TUNNEL("small_tunnel"),
    /** A 1x2 walking tunnel straight ahead. */
    MINING_TUNNEL("mining_tunnel"),
    /** A 3x3 tunnel straight ahead. */
    LARGE_TUNNEL("large_tunnel"),
    /** A 3x3 square on the face that was hit. */
    SMALL_SQUARE("small_square"),
    /** A staircase going up and away, to climb out of a mine. */
    ESCAPE_TUNNEL("escape_tunnel");

    private final String name;

    VeinShape(String name) {
        this.name = name;
    }

    public String id() {
        return name;
    }

    public Component displayName() {
        return Component.translatable("corerpg_quests.vein.shape." + name);
    }

    public boolean shaped() {
        return this != SHAPELESS;
    }

    public static VeinShape byIndex(int index) {
        VeinShape[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
