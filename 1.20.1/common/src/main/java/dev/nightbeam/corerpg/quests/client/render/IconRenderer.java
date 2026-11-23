package dev.nightbeam.corerpg.quests.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nightbeam.corerpg.quests.book.Icon;
import net.minecraft.client.gui.GuiGraphics;

/** Draws quest object icons (items or textures) at any size. */
public final class IconRenderer {
    private IconRenderer() {
    }

    /** Draws the icon centred in a {@code size} x {@code size} box at (x, y). */
    public static void render(GuiGraphics g, Icon icon, float x, float y, float size) {
        if (icon.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 16f, size / 16f, 1);
        if (icon.texture() != null) {
            RenderSystem.enableBlend();
            g.blit(icon.texture(), 0, 0, 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();
        } else {
            g.renderFakeItem(icon.stack(), 0, 0);
        }
        g.pose().popPose();
    }

    public static void render(GuiGraphics g, Icon icon, int x, int y) {
        render(g, icon, x, y, 16);
    }

    public static void renderSmall(GuiGraphics g, Icon icon, int x, int y) {
        render(g, icon, x, y, 10);
    }
}
