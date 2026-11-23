package dev.nightbeam.corerpg.quests.client.render;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * The quest book's look: warm parchment text on deep ash, ember and gold accents. Panels are drawn procedurally so
 * they scale cleanly at any GUI scale.
 */
public final class Theme {
    public static final int BG = 0xF2141012;
    public static final int BG_SOFT = 0xE61C1618;
    public static final int BG_RAISED = 0xF0241C1C;
    public static final int LINE = 0xFF3B2F2B;
    public static final int LINE_SOFT = 0x803B2F2B;
    public static final int TEXT = 0xFFE6D8C3;
    public static final int MUTED = 0xFF9C8E7E;
    public static final int FAINT = 0xFF6A5E54;
    public static final int TITLE = 0xFFF5E6C8;
    public static final int EMBER = 0xFFE8743B;
    public static final int GOLD = 0xFFE8C15A;
    public static final int SAGE = 0xFF8FC27A;
    public static final int SKY = 0xFF6FB6E0;
    public static final int CRIMSON = 0xFFD9534F;
    public static final int VIOLET = 0xFFB58CE6;

    public static final ResourceLocation DISPLAY_FONT = CoreQuests.id("display");
    public static final ResourceLocation TITLE_FONT = CoreQuests.id("title");

    private Theme() {
    }

    /** Text in the display serif (Cinzel), unless the player turned fancy fonts off. */
    public static MutableComponent display(Component text) {
        return styled(text, DISPLAY_FONT);
    }

    /** Large title serif. */
    public static MutableComponent title(Component text) {
        return styled(text, TITLE_FONT);
    }

    private static MutableComponent styled(Component text, ResourceLocation font) {
        MutableComponent copy = text.copy();
        return CoreConfig.CLIENT.fancyFonts.get() ? copy.withStyle(s -> s.withFont(font)) : copy;
    }

    /** Draws styled text cut to a width (with an ellipsis), keeping its font and colours. */
    public static void drawFitted(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width("…")));
        g.drawString(font, Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…"))), x, y, color, false);
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | color & 0xFFFFFF;
    }

    public static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF, aa = a >>> 24;
        int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF, ba = b >>> 24;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    /** A framed panel: dark body, a 1px border, a top accent line and small corner marks. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG_SOFT);
        outline(g, x, y, w, h, LINE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(accent, 0xC0));
        corner(g, x, y, w, h, accent);
    }

    /** A lighter panel for the HUD tracker. */
    public static void hudPanel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, 0xB0100C0C);
        g.fill(x, y, x + 1, y + h, withAlpha(accent, 0xE0));
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void corner(GuiGraphics g, int x, int y, int w, int h, int accent) {
        int c = withAlpha(accent, 0xFF);
        g.fill(x, y, x + 4, y + 1, c);
        g.fill(x, y, x + 1, y + 4, c);
        g.fill(x + w - 4, y, x + w, y + 1, c);
        g.fill(x + w - 1, y, x + w, y + 4, c);
        g.fill(x, y + h - 1, x + 4, y + h, c);
        g.fill(x, y + h - 4, x + 1, y + h, c);
        g.fill(x + w - 4, y + h - 1, x + w, y + h, c);
        g.fill(x + w - 1, y + h - 4, x + w, y + h, c);
    }

    /** A thin horizontal separator that fades out at both ends. */
    public static void separator(GuiGraphics g, int x, int y, int w, int color) {
        int fade = Math.min(24, w / 4);
        g.fillGradient(x, y, x + fade, y + 1, withAlpha(color, 0), color);
        g.fill(x + fade, y, x + w - fade, y + 1, color);
        for (int i = 0; i < fade; i++) {
            float t = 1 - (i + 1) / (float) fade;
            g.fill(x + w - fade + i, y, x + w - fade + i + 1, y + 1, withAlpha(color, (int) ((color >>> 24) * t)));
        }
    }

    /** A progress bar with a dark track. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
        g.fill(x, y, x + w, y + h, 0x80000000);
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            g.fill(x, y, x + filled, y + 1, withAlpha(0xFFFFFF, 0x40));
        }
    }
}
