package dev.nightbeam.corerpg.quests.client.screen;

import dev.nightbeam.corerpg.quests.client.render.GuiIcons;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A right-click menu with optional icons, separators and one level of sub-menus. */
public final class ContextMenu {
    private static final int ROW = 13;

    public static final class Entry {
        final Component label;
        final @Nullable String icon;
        final @Nullable Runnable action;
        final List<Entry> children = new ArrayList<>();
        boolean enabled = true;
        boolean separator;
        int color = Theme.TEXT;

        Entry(Component label, @Nullable String icon, @Nullable Runnable action) {
            this.label = label;
            this.icon = icon;
            this.action = action;
        }

        public Entry disabled(boolean disabled) {
            this.enabled = !disabled;
            return this;
        }

        public Entry danger() {
            this.color = Theme.CRIMSON;
            return this;
        }

        public Entry add(Component label, @Nullable String icon, Runnable action) {
            Entry e = new Entry(label, icon, action);
            children.add(e);
            return e;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private @Nullable Entry openSub;
    private int subX;
    private int subY;
    private int subWidth;

    public Entry add(Component label, @Nullable String icon, @Nullable Runnable action) {
        Entry e = new Entry(label, icon, action);
        entries.add(e);
        return e;
    }

    public void separator() {
        Entry e = new Entry(Component.empty(), null, null);
        e.separator = true;
        entries.add(e);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void open(Font font, int mouseX, int mouseY, int screenW, int screenH) {
        width = measure(font, entries);
        int height = height(entries);
        x = Math.min(mouseX, screenW - width - 2);
        y = Math.min(mouseY, screenH - height - 2);
        openSub = null;
    }

    private static int measure(Font font, List<Entry> list) {
        int w = 60;
        for (Entry e : list) {
            w = Math.max(w, font.width(e.label) + 28 + (e.children.isEmpty() ? 0 : 10));
        }
        return w;
    }

    private static int height(List<Entry> list) {
        int h = 4;
        for (Entry e : list) {
            h += e.separator ? 5 : ROW;
        }
        return h;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Entry hovered = draw(g, font, entries, x, y, width, mouseX, mouseY);
        if (hovered != null && !hovered.children.isEmpty() && hovered.enabled) {
            if (openSub != hovered) {
                openSub = hovered;
                subWidth = measure(font, hovered.children);
                int index = entries.indexOf(hovered);
                int rowY = y + 2;
                for (int i = 0; i < index; i++) {
                    rowY += entries.get(i).separator ? 5 : ROW;
                }
                subX = x + width - 2 + subWidth > screenW ? x - subWidth + 2 : x + width - 2;
                subY = Math.min(rowY - 2, screenH - height(hovered.children) - 2);
            }
        } else if (hovered != null && hovered.children.isEmpty() && !insideSub(mouseX, mouseY)) {
            openSub = null;
        }
        if (openSub != null) {
            draw(g, font, openSub.children, subX, subY, subWidth, mouseX, mouseY);
        }
        g.pose().popPose();
    }

    private boolean insideSub(double mx, double my) {
        return openSub != null && mx >= subX && mx < subX + subWidth && my >= subY && my < subY + height(openSub.children);
    }

    private static @Nullable Entry draw(GuiGraphics g, Font font, List<Entry> list, int x, int y, int w, int mx, int my) {
        int h = height(list);
        Theme.panel(g, x, y, w, h, Theme.EMBER);
        Entry hovered = null;
        int rowY = y + 2;
        for (Entry e : list) {
            if (e.separator) {
                g.fill(x + 4, rowY + 2, x + w - 4, rowY + 3, Theme.LINE);
                rowY += 5;
                continue;
            }
            boolean hover = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW;
            if (hover) {
                hovered = e;
                if (e.enabled) {
                    g.fill(x + 1, rowY, x + w - 1, rowY + ROW, 0x40E8743B);
                }
            }
            int color = e.enabled ? e.color : Theme.FAINT;
            if (e.icon != null) {
                GuiIcons.draw(g, e.icon, x + 4, rowY + 2, 9, color);
            }
            g.drawString(font, e.label, x + 16, rowY + 3, color, false);
            if (!e.children.isEmpty()) {
                g.drawString(font, "›", x + w - 9, rowY + 3, Theme.MUTED, false);
            }
            rowY += ROW;
        }
        return hovered;
    }

    /** Returns true when the click landed on the menu (the menu should then close). */
    public boolean click(double mx, double my) {
        Entry e = hit(entries, x, y, width, mx, my);
        if (e == null && openSub != null) {
            e = hit(openSub.children, subX, subY, subWidth, mx, my);
        }
        if (e != null && e.enabled && e.action != null) {
            e.action.run();
            return true;
        }
        return e != null && !e.children.isEmpty();
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height(entries) || insideSub(mx, my);
    }

    private static @Nullable Entry hit(List<Entry> list, int x, int y, int w, double mx, double my) {
        int rowY = y + 2;
        for (Entry e : list) {
            int h = e.separator ? 5 : ROW;
            if (!e.separator && mx >= x && mx < x + w && my >= rowY && my < rowY + h) {
                return e;
            }
            rowY += h;
        }
        return null;
    }
}
