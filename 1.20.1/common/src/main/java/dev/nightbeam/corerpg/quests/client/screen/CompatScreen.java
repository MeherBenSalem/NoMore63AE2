package dev.nightbeam.corerpg.quests.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Minecraft 1.20.1 edition: gives every quest screen the 1.20.2+ screen shape, so the screens are the same code on
 * every version. The background is drawn by {@link #render} before the widgets, and scrolling has a horizontal
 * amount (always 0 here).
 */
public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
    }

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return mouseScrolled(mouseX, mouseY, 0, delta);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }
}
