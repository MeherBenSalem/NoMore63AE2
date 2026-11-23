package dev.nightbeam.corerpg.quests.client;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * The quest screens number mouse buttons the classic way (left 0, right 1, middle 2).
 * Minecraft 26.3 reports SDL button numbers, so every button read goes through here.
 */
public final class InputCompat {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int MIDDLE = 2;

    private InputCompat() {
    }

    public static int button(int raw) {
        if (raw == InputConstants.MOUSE_BUTTON_LEFT) {
            return LEFT;
        }
        if (raw == InputConstants.MOUSE_BUTTON_RIGHT) {
            return RIGHT;
        }
        if (raw == InputConstants.MOUSE_BUTTON_MIDDLE) {
            return MIDDLE;
        }
        return raw + 16;
    }
}
