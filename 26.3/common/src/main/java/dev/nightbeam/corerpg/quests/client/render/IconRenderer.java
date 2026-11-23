package dev.nightbeam.corerpg.quests.client.render;

import dev.nightbeam.corerpg.quests.book.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/** Draws quest object icons (items or textures) at any size. */
public final class IconRenderer {
    private IconRenderer() {
    }

    /** Draws the icon centred in a {@code size} x {@code size} box at (x, y). */
    public static void render(GuiGraphicsExtractor g, Icon icon, float x, float y, float size) {
        render(g, icon, x, y, size, 0xFFFFFFFF);
    }

    /** Like {@link #render(GuiGraphicsExtractor, Icon, float, float, float)}, tinting texture icons with {@code tint}. */
    public static void render(GuiGraphicsExtractor g, Icon icon, float x, float y, float size, int tint) {
        if (icon.isEmpty()) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        if (icon.texture() != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), 0, 0, 0f, 0f, 16, 16, 16, 16, tint);
        } else {
            g.fakeItem(icon.stack(), 0, 0);
        }
        g.pose().popMatrix();
    }

    public static void render(GuiGraphicsExtractor g, Icon icon, int x, int y) {
        render(g, icon, x, y, 16);
    }

    public static void renderSmall(GuiGraphicsExtractor g, Icon icon, int x, int y) {
        render(g, icon, x, y, 10);
    }
}
