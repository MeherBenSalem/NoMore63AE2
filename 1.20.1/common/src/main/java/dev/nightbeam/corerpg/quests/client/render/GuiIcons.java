package dev.nightbeam.corerpg.quests.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nightbeam.corerpg.quests.CoreQuests;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** The 16x16 white glyphs in textures/gui/icon/, drawn tinted. */
public final class GuiIcons {
    private GuiIcons() {
    }

    public static ResourceLocation texture(String name) {
        return CoreQuests.id("textures/gui/icon/" + name + ".png");
    }

    public static void draw(GuiGraphics g, String name, float x, float y, float size, int color) {
        float a = (color >>> 24) / 255f;
        if (a == 0) {
            a = 1;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 16f, size / 16f, 1);
        RenderSystem.enableBlend();
        g.setColor((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f, a);
        g.blit(texture(name), 0, 0, 0, 0, 16, 16, 16, 16);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    public static void draw(GuiGraphics g, String name, int x, int y, int color) {
        draw(g, name, x, y, 16, color);
    }
}
