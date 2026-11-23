package dev.nightbeam.corerpg.quests.client.screen.team;

import dev.nightbeam.corerpg.quests.client.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small inline action and label chips used in lists (Accept, Kick, stage names...). */
public final class Chip {
    public static final int HEIGHT = 11;

    private Chip() {
    }

    public static int width(Font font, Component text) {
        return font.width(text) + 8;
    }

    /** Draws a chip and returns its width. */
    public static int draw(GuiGraphics g, Font font, Component text, int x, int y, int accent, boolean hovered) {
        int w = width(font, text);
        g.fill(x, y, x + w, y + HEIGHT, Theme.withAlpha(accent, hovered ? 0x55 : 0x22));
        Theme.outline(g, x, y, w, HEIGHT, Theme.withAlpha(accent, hovered ? 0xFF : 0xA0));
        g.drawString(font, text, x + 4, y + 2, hovered ? Theme.TITLE : accent | 0xFF000000, false);
        return w;
    }
}
