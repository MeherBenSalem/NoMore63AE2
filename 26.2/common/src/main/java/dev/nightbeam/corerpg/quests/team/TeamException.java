package dev.nightbeam.corerpg.quests.team;

import net.minecraft.network.chat.Component;

/** A team action that is not allowed; the message is shown to the player. */
public class TeamException extends Exception {
    private final Component message;

    public TeamException(String key, Object... args) {
        super(key);
        this.message = Component.translatable(key, args);
    }

    public Component component() {
        return message;
    }
}
