package dev.nightbeam.corerpg.quests.client.render;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.QuestShape;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws quest node shapes (tinted masks) and connection lines on the chapter canvas. */
public final class ShapeRenderer {
    private ShapeRenderer() {
    }

    private static Identifier fill(QuestShape shape) {
        return CoreQuests.id("textures/gui/shape/" + shape.texture() + ".png");
    }

    private static Identifier outline(QuestShape shape) {
        return CoreQuests.id("textures/gui/shape/" + shape.texture() + "_outline.png");
    }

    /** Draws a shape centred on (cx, cy) with the given diameter: a filled body and an outline ring. */
    public static void node(GuiGraphicsExtractor g, QuestShape shape, float cx, float cy, float size, int fillColor, int outlineColor) {
        if (shape == QuestShape.NONE || shape == QuestShape.DEFAULT) {
            return;
        }
        blit(g, fill(shape), cx, cy, size, fillColor);
        blit(g, outline(shape), cx, cy, size, outlineColor);
    }

    /** A soft glow behind a node: the fill mask drawn larger and faint. */
    public static void glow(GuiGraphicsExtractor g, QuestShape shape, float cx, float cy, float size, int color) {
        if (shape == QuestShape.NONE || shape == QuestShape.DEFAULT) {
            return;
        }
        blit(g, fill(shape), cx, cy, size * 1.35f, Theme.withAlpha(color, (color >>> 24) / 4));
        blit(g, fill(shape), cx, cy, size * 1.18f, Theme.withAlpha(color, (color >>> 24) / 3));
    }

    private static void blit(GuiGraphicsExtractor g, Identifier texture, float cx, float cy, float size, int color) {
        g.pose().pushMatrix();
        g.pose().translate(cx - size / 2f, cy - size / 2f);
        g.pose().scale(size / 64f, size / 64f);
        g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, 64, 64, 64, 64, color);
        g.pose().popMatrix();
    }

    /** A straight line of the given thickness between two points. */
    public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5f) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x1, y1);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.pose().scale(length / 64f, thickness / 2f);
        g.fill(0, -1, 64, 1, color);
        g.pose().popMatrix();
    }

    /** A dashed line whose dashes move along it over time (animated "flow" toward the end point). */
    public static void dashed(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float thickness, float dash, float gap, float offset, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5f) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float period = dash + gap;
        float start = -(period - offset % period);
        for (float t = start; t < length; t += period) {
            float a = Math.max(0, t);
            float b = Math.min(length, t + dash);
            if (b > a) {
                line(g, x1 + ux * a, y1 + uy * a, x1 + ux * b, y1 + uy * b, thickness, color);
            }
        }
    }

    /** A small filled arrow head at (x, y) pointing along the direction (dx, dy). */
    public static void arrowHead(GuiGraphicsExtractor g, float x, float y, float dx, float dy, float size, int color) {
        float angle = (float) Math.atan2(dy, dx);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate(angle);
        float s = size / 8f;
        g.pose().scale(s, s);
        // Tip at the end point, widening backwards along the line.
        for (int i = 0; i < 8; i++) {
            int half = (i + 1) / 2;
            g.fill(-i - 1, -half, -i, half + 1, color);
        }
        g.pose().popMatrix();
    }
}
