package dev.nightbeam.corerpg.quests.client.screen.editor;

import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.render.GuiIcons;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.widget.ThemedButton;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Picks several quests or chapters (dependencies): a checklist grouped by chapter, with search. */
public class IdListScreen extends Screen {
    private record Entry(QuestObject object, boolean header) {
    }

    private final Screen parent;
    private final Set<String> selected;
    private final QuestObject owner;
    private final Consumer<List<String>> onSave;
    private final List<Entry> entries = new ArrayList<>();
    private EditBox search;
    private double scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public IdListScreen(Screen parent, Component title, List<String> initial, QuestObject owner, Consumer<List<String>> onSave) {
        super(title);
        this.parent = parent;
        this.selected = new LinkedHashSet<>(initial);
        this.owner = owner;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(340, width - 16);
        panelHeight = Math.min(340, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        search = new EditBox(font, left + 8, top + 20, panelWidth - 16, 14, Component.translatable("corerpg_quests.gui.search"));
        search.setResponder(s -> {
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        int by = top + panelHeight - 22;
        addRenderableWidget(new ThemedButton(left + 8, by, 70, 16, Component.translatable("corerpg_quests.gui.clear"), b -> selected.clear(), Theme.MUTED));
        addRenderableWidget(new ThemedButton(left + panelWidth - 154, by, 70, 16, Component.translatable("corerpg_quests.gui.save"), b -> {
            onSave.accept(new ArrayList<>(selected));
            minecraft.setScreen(parent);
        }, Theme.SAGE));
        addRenderableWidget(new ThemedButton(left + panelWidth - 78, by, 70, 16, Component.translatable("gui.cancel"), b -> minecraft.setScreen(parent), Theme.MUTED));
        filter();
    }

    private void filter() {
        entries.clear();
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return;
        }
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Chapter c : book.chapters) {
            boolean chapterMatch = q.isEmpty() || TextUtil.plain(c.displayTitle().getString()).toLowerCase(Locale.ROOT).contains(q);
            List<Entry> quests = new ArrayList<>();
            for (Quest quest : c.quests) {
                if (quest == owner) {
                    continue;
                }
                String hay = (TextUtil.plain(quest.displayTitle().getString()) + " " + quest.id()).toLowerCase(Locale.ROOT);
                if (q.isEmpty() || chapterMatch || hay.contains(q)) {
                    quests.add(new Entry(quest, false));
                }
            }
            if (!quests.isEmpty() || chapterMatch) {
                entries.add(new Entry(c, true));
                entries.addAll(quests);
            }
        }
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
        String count = Component.translatable("corerpg_quests.gui.selected", selected.size()).getString();
        g.drawString(font, count, left + panelWidth - 8 - font.width(count), top + 7, Theme.MUTED, false);
        int lt = top + 40;
        int lb = top + panelHeight - 28;
        g.enableScissor(left + 2, lt, left + panelWidth - 2, lb);
        int y = lt - (int) scroll;
        for (Entry e : entries) {
            if (y > lb) {
                break;
            }
            if (y + 14 >= lt) {
                boolean hover = mouseX >= left + 4 && mouseX < left + panelWidth - 4 && mouseY >= y && mouseY < y + 14 && mouseY >= lt && mouseY < lb;
                int indent = e.header ? 6 : 18;
                boolean checked = selected.contains(e.object.id());
                if (hover) {
                    g.fill(left + 4, y, left + panelWidth - 4, y + 14, 0x30FFFFFF);
                }
                g.fill(left + indent, y + 3, left + indent + 8, y + 11, 0xFF1A1414);
                Theme.outline(g, left + indent, y + 3, 8, 8, checked ? Theme.GOLD : Theme.LINE);
                if (checked) {
                    GuiIcons.draw(g, "check", left + indent, y + 3, 8, Theme.GOLD);
                }
                IconRenderer.render(g, e.object.displayIcon(), left + indent + 12, y + 2, 10);
                g.drawString(font, font.substrByWidth(e.object.displayTitle(), panelWidth - indent - 40).getString(), left + indent + 26, y + 3,
                        e.header ? Theme.GOLD : Theme.TEXT, false);
            }
            y += 14;
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int lt = top + 40;
        int lb = top + panelHeight - 28;
        if (my >= lt && my < lb && mx >= left && mx < left + panelWidth) {
            int index = (int) ((my - lt + scroll) / 14);
            if (index >= 0 && index < entries.size()) {
                String id = entries.get(index).object.id();
                if (!selected.remove(id)) {
                    selected.add(id);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, entries.size() * 14 - (panelHeight - 68));
        scroll = Math.max(0, Math.min(max, scroll - sy * 28));
        return true;
    }
}
