package dev.nightbeam.corerpg.quests.client.screen.editor;

import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.widget.ThemedButton;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Multi-line text with formatting buttons and a live preview (descriptions, hover text, subtitles). */
public class TextEditScreen extends Screen {
    private static final String[][] CODES = {
            {"&0", "0"}, {"&1", "1"}, {"&2", "2"}, {"&3", "3"}, {"&4", "4"}, {"&5", "5"}, {"&6", "6"}, {"&7", "7"},
            {"&8", "8"}, {"&9", "9"}, {"&a", "a"}, {"&b", "b"}, {"&c", "c"}, {"&d", "d"}, {"&e", "e"}, {"&f", "f"},
            {"&l", "B"}, {"&o", "I"}, {"&n", "U"}, {"&m", "S"}, {"&r", "R"}
    };

    private final Screen parent;
    private final List<String> initial;
    private final Consumer<List<String>> onSave;
    private MultiLineEditBox editor;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private double previewScroll;

    public TextEditScreen(Screen parent, Component title, List<String> initial, Consumer<List<String>> onSave) {
        super(title);
        this.parent = parent;
        this.initial = new ArrayList<>(initial);
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(620, width - 16);
        panelHeight = Math.min(380, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        String text = editor != null ? editor.getValue() : String.join("\n", initial);
        int editW = panelWidth / 2 - 12;
        editor = new MultiLineEditBox(font, left + 8, top + 40, editW, panelHeight - 70,
                Component.translatable("corerpg_quests.gui.text_hint"), Component.empty());
        editor.setCharacterLimit(20000);
        editor.setValue(text);
        addRenderableWidget(editor);
        setInitialFocus(editor);
        int bx = left + 8;
        for (String[] code : CODES) {
            String insert = code[0];
            ThemedButton b = new ThemedButton(bx, top + 22, 13, 14, Component.literal(TextUtil.toSection(insert + code[1])), btn -> insert(insert), Theme.GOLD);
            b.setTooltip(Tooltip.create(Component.literal(insert)));
            addRenderableWidget(b);
            bx += 14;
        }
        ThemedButton page = new ThemedButton(bx + 4, top + 22, 26, 14, Component.literal("¶"), btn -> insert("\n" + TextUtil.PAGE_BREAK + "\n"), Theme.SKY);
        page.setTooltip(Tooltip.create(Component.translatable("corerpg_quests.gui.page_break")));
        addRenderableWidget(page);
        ThemedButton image = new ThemedButton(bx + 32, top + 22, 26, 14, Component.literal("▣"), btn -> insert("\n{image:minecraft:textures/item/map.png 64}\n"), Theme.SKY);
        image.setTooltip(Tooltip.create(Component.translatable("corerpg_quests.gui.insert_image")));
        addRenderableWidget(image);
        int by = top + panelHeight - 22;
        addRenderableWidget(new ThemedButton(left + panelWidth - 154, by, 70, 16, Component.translatable("corerpg_quests.gui.save"), b -> save(), Theme.SAGE));
        addRenderableWidget(new ThemedButton(left + panelWidth - 78, by, 70, 16, Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent), Theme.MUTED));
    }

    private void insert(String text) {
        // MultiLineEditBox has no public insert; emulate by typing the characters at the cursor.
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            } else {
                editor.charTyped(c, 0);
            }
        }
        setFocused(editor);
    }

    private void save() {
        List<String> lines = new ArrayList<>(List.of(editor.getValue().split("\n", -1)));
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        onSave.accept(lines);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Drawn first in render(); Screen.render() calls this again after our panel, so it must stay empty.
    }

    private void drawOverlay(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawOverlay(g);
        Theme.panel(g, left, top, panelWidth, panelHeight, Theme.GOLD);
        g.drawString(font, title, left + 8, top + 7, Theme.GOLD, false);
        Component help = Component.translatable("corerpg_quests.gui.text_help").withStyle(ChatFormatting.DARK_GRAY);
        g.drawString(font, font.substrByWidth(help, panelWidth / 2 - 10).getString(), left + panelWidth / 2 + 4, top + 7, Theme.FAINT, false);
        int px = left + panelWidth / 2 + 4;
        int pw = panelWidth / 2 - 12;
        int py = top + 40;
        int ph = panelHeight - 70;
        g.fill(px, py, px + pw, py + ph, 0x60000000);
        Theme.outline(g, px, py, pw, ph, Theme.LINE);
        g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
        int y = py + 4 - (int) previewScroll;
        for (String raw : editor.getValue().split("\n", -1)) {
            if (raw.trim().equals(TextUtil.PAGE_BREAK)) {
                g.fill(px + 4, y + 4, px + pw - 4, y + 5, Theme.SKY);
                y += 10;
                continue;
            }
            if (raw.isBlank()) {
                y += 6;
                continue;
            }
            for (FormattedCharSequence line : font.split(TextUtil.format(raw), pw - 8)) {
                g.drawString(font, line, px + 4, y, Theme.TEXT, false);
                y += 10;
            }
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx > left + panelWidth / 2) {
            previewScroll = Math.max(0, previewScroll - sy * 10);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
}
