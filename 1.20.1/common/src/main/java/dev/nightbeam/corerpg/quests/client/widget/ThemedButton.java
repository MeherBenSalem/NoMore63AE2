package dev.nightbeam.corerpg.quests.client.widget;

import dev.nightbeam.corerpg.quests.client.render.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A button in the quest book style: dark body, thin border and an accent underline that lights up on hover. */
public class ThemedButton extends Button {
    private int accent;

    public ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress, int accent) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.accent = accent;
    }

    public void setAccent(int accent) {
        this.accent = accent;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean hot = active && isHoveredOrFocused();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        g.fill(x, y, x + w, y + h, hot ? Theme.mix(Theme.BG_RAISED, accent | 0xFF000000, 0.18f) : Theme.BG_RAISED);
        Theme.outline(g, x, y, w, h, !active ? Theme.LINE_SOFT : hot ? accent | 0xFF000000 : Theme.LINE);
        if (active) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Theme.withAlpha(accent, hot ? 0xFF : 0x90));
        }
        int color = !active ? Theme.FAINT : hot ? Theme.TITLE : Theme.TEXT;
        Component message = getMessage();
        int textWidth = font.width(message);
        if (textWidth > w - 6) {
            renderScrollingString(g, font, 3, color);
        } else {
            g.drawString(font, message, x + (w - textWidth) / 2, y + (h - 8) / 2, color, false);
        }
    }
}
