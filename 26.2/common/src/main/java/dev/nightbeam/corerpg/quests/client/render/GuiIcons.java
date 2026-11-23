package dev.nightbeam.corerpg.quests.client.render;

import dev.nightbeam.corerpg.quests.CoreQuests;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** The 16x16 white glyphs in textures/gui/icon/, drawn tinted. */
public final class GuiIcons {
    private GuiIcons() {
    }

    public static Identifier texture(String name) {
        return CoreQuests.id("textures/gui/icon/" + name + ".png");
    }

    public static void draw(GuiGraphicsExtractor g, String name, float x, float y, float size, int color) {
        float a = (color >>> 24) / 255f;
        if (a == 0) {
            a = 1;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        int argb = (Math.round(a * 255f) << 24) | (color & 0xFFFFFF);
        g.blit(RenderPipelines.GUI_TEXTURED, texture(name), 0, 0, 0f, 0f, 16, 16, 16, 16, argb);
        g.pose().popMatrix();
    }

    public static void draw(GuiGraphicsExtractor g, String name, int x, int y, int color) {
        draw(g, name, x, y, 16, color);
    }
}
