package dev.nightbeam.corerpg.quests.team;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** A player's standing in a team. Members are MEMBER, OFFICER and OWNER; ALLY and INVITED are outsiders with a link. */
public enum TeamRank implements StringRepresentable {
    NONE("none", 0, ChatFormatting.GRAY),
    ALLY("ally", 10, ChatFormatting.DARK_AQUA),
    INVITED("invited", 20, ChatFormatting.YELLOW),
    MEMBER("member", 50, ChatFormatting.WHITE),
    OFFICER("officer", 75, ChatFormatting.AQUA),
    OWNER("owner", 100, ChatFormatting.GOLD);

    private final String name;
    public final int power;
    public final ChatFormatting color;

    TeamRank(String name, int power, ChatFormatting color) {
        this.name = name;
        this.power = power;
        this.color = color;
    }

    public boolean isMember() {
        return power >= MEMBER.power;
    }

    public boolean isOfficerOrAbove() {
        return power >= OFFICER.power;
    }

    public boolean isAllyOrMember() {
        return this == ALLY || isMember();
    }

    public Component displayName() {
        return Component.translatable("corerpg_quests.team.rank." + name).withStyle(color);
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static TeamRank byName(String name) {
        for (TeamRank rank : values()) {
            if (rank.name.equals(name)) {
                return rank;
            }
        }
        return NONE;
    }
}
