package dev.nightbeam.corerpg.quests.team;

import net.minecraft.util.StringRepresentable;

/** PLAYER: everyone's personal team. PARTY: player-made groups. SERVER: admin-made teams (factions, events). */
public enum TeamType implements StringRepresentable {
    PLAYER("player"),
    PARTY("party"),
    SERVER("server");

    private final String name;

    TeamType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static TeamType byName(String name) {
        for (TeamType type : values()) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return PLAYER;
    }
}
