package dev.nightbeam.corerpg.quests.client.screen.editor;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.ObjectKind;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.RewardTable;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.render.GuiIcons;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.screen.ContextMenu;
import dev.nightbeam.corerpg.quests.client.screen.EditorActions;
import dev.nightbeam.corerpg.quests.client.widget.ThemedButton;
import dev.nightbeam.corerpg.quests.item.LootCrateItem;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reward table editor: tables on the left, the selected table's weighted entries on the right. */
public class RewardTablesScreen extends Screen {
    private final Screen parent;
    private @Nullable String selectedId;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int split;
    private double tableScroll;
    private double entryScroll;
    private int lastVersion = -1;
    private @Nullable ContextMenu menu;
    private @Nullable List<Component> tooltip;

    public RewardTablesScreen(Screen parent) {
        super(Component.translatable("corerpg_quests.gui.reward_tables"));
        this.parent = parent;
    }

    private QuestBook book() {
        QuestBook book = ClientQuests.book();
        return book != null ? book : new QuestBook();
    }

    private @Nullable RewardTable selected() {
        return selectedId == null ? null : book().table(selectedId);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(520, width - 16);
        panelHeight = Math.min(340, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        split = 170;
        if (selectedId == null && !book().tables.isEmpty()) {
            selectedId = book().tables.getFirst().id();
        }
        int by = top + panelHeight - 22;
        addRenderableWidget(new ThemedButton(left + 8, by, 74, 16, Component.translatable("corerpg_quests.gui.new_table"), b -> {
            JsonObject data = new JsonObject();
            data.addProperty("title", "New Table");
            EditorActions.focusNextCreated("table");
            EditorActions.create(ObjectKind.TABLE, "", data);
        }, Theme.EMBER));
        addRenderableWidget(new ThemedButton(left + split + 8, by, 74, 16, Component.translatable("corerpg_quests.gui.add_entry"), b -> addEntryMenu(), Theme.GOLD));
        addRenderableWidget(new ThemedButton(left + split + 86, by, 74, 16, Component.translatable("corerpg_quests.gui.table_settings"), b -> {
            RewardTable t = selected();
            if (t != null) {
                minecraft.setScreen(new PropertyEditorScreen(this, t));
            }
        }, Theme.GOLD));
        addRenderableWidget(new ThemedButton(left + panelWidth - 62, by, 54, 16, Component.translatable("gui.done"), b -> onClose(), Theme.MUTED));
    }

    @Override
    public void tick() {
        if (ClientQuests.version() != lastVersion) {
            lastVersion = ClientQuests.version();
            String created = ClientQuests.takeLastCreated();
            if (created != null && EditorActions.hasPendingFocus()) {
                String hint = EditorActions.takePendingFocus();
                if (hint.equals("table") && book().table(created) != null) {
                    selectedId = created;
                } else if (book().get(created) instanceof dev.nightbeam.corerpg.quests.reward.Reward reward) {
                    minecraft.setScreen(new PropertyEditorScreen(this, reward));
                }
            }
        }
    }

    private void addEntryMenu() {
        RewardTable table = selected();
        if (table == null) {
            return;
        }
        ContextMenu m = new ContextMenu();
        for (RewardType<?> type : RewardTypes.all()) {
            m.add(Component.translatable(type.translationKey()), null, () -> {
                JsonObject data = new JsonObject();
                data.addProperty("type", type.shortId());
                data.addProperty("weight", 1);
                EditorActions.focusNextCreated("entry");
                EditorActions.create(ObjectKind.REWARD, table.id(), data);
            });
        }
        m.open(font, left + split + 8, top + panelHeight - 40, width, height);
        menu = m;
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
        tooltip = null;
        drawOverlay(g);
        int mx = menu != null ? -1000 : mouseX;
        int my = menu != null ? -1000 : mouseY;
        Theme.panel(g, left, top, panelWidth, panelHeight, Theme.GOLD);
        g.drawString(font, title, left + 8, top + 7, Theme.GOLD, false);
        g.fill(left + split, top + 20, left + split + 1, top + panelHeight - 28, Theme.LINE);
        int lt = top + 22;
        int lb = top + panelHeight - 28;

        g.enableScissor(left + 2, lt, left + split - 1, lb);
        int y = lt - (int) tableScroll;
        for (RewardTable table : book().tables) {
            boolean sel = table.id().equals(selectedId);
            boolean hover = mx >= left + 2 && mx < left + split && my >= y && my < y + 16 && my >= lt && my < lb;
            if (sel) {
                g.fill(left + 2, y, left + split - 1, y + 16, 0x50E8C15A);
            } else if (hover) {
                g.fill(left + 2, y, left + split - 1, y + 16, 0x20FFFFFF);
            }
            IconRenderer.render(g, table.displayIcon(), left + 6, y + 2, 12);
            g.drawString(font, font.substrByWidth(table.displayTitle(), split - 40).getString(), left + 22, y + 4, sel ? Theme.TITLE : Theme.TEXT, false);
            if (table.lootCrate) {
                GuiIcons.draw(g, "gift", left + split - 14, y + 3, 10, Theme.GOLD);
            }
            y += 16;
        }
        g.disableScissor();

        RewardTable table = selected();
        if (table != null) {
            int rx = left + split + 8;
            int rw = panelWidth - split - 16;
            String info = Component.translatable("corerpg_quests.gui.table_info", table.rolls, trim(table.emptyWeight), table.entries.size()).getString();
            g.drawString(font, font.plainSubstrByWidth(info, rw), rx, lt, Theme.MUTED, false);
            if (table.lootCrate) {
                g.drawString(font, Component.translatable("corerpg_quests.gui.table_crate",
                        trim(table.passiveDropChance), trim(table.monsterDropChance), trim(table.bossDropChance)).withStyle(ChatFormatting.GOLD), rx, lt + 10, Theme.GOLD, false);
                g.renderItem(LootCrateItem.of(table), rx + rw - 18, lt - 2);
            }
            int et = lt + 24;
            g.enableScissor(rx - 2, et, left + panelWidth - 2, lb);
            y = et - (int) entryScroll;
            for (RewardTable.Entry entry : table.entries) {
                boolean hover = mx >= rx && mx < rx + rw && my >= y && my < y + 18 && my >= et && my < lb;
                g.fill(rx, y, rx + rw, y + 18, hover ? 0x30FFFFFF : 0x18FFFFFF);
                IconRenderer.render(g, entry.reward.displayIcon(), rx + 3, y + 1, 16);
                String chance = String.format(Locale.ROOT, "%s  ·  %.1f%%", trim(entry.weight), table.chance(entry));
                g.drawString(font, chance, rx + rw - 4 - font.width(chance), y + 5, Theme.GOLD, false);
                g.drawString(font, font.substrByWidth(entry.reward.displayTitle(), rw - 30 - font.width(chance)).getString(), rx + 23, y + 5, Theme.TEXT, false);
                if (hover) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(entry.reward.displayTitle());
                    entry.reward.addTooltip(lines);
                    lines.add(Component.translatable("corerpg_quests.gui.entry_hint").withStyle(ChatFormatting.DARK_GRAY));
                    tooltip = lines;
                }
                y += 20;
            }
            g.disableScissor();
        } else {
            g.drawString(font, Component.translatable("corerpg_quests.gui.no_tables"), left + split + 10, lt + 4, Theme.MUTED, false);
        }
        super.render(g, mx, my, partialTick);
        if (menu != null) {
            menu.render(g, font, mouseX, mouseY, width, height);
        } else if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.2f", d);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu != null) {
            boolean inside = menu.contains(mx, my);
            boolean consumed = menu.click(mx, my);
            if (consumed || !inside) {
                menu = null;
            }
            return true;
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int lt = top + 22;
        int lb = top + panelHeight - 28;
        if (my < lt || my >= lb) {
            return false;
        }
        if (mx >= left && mx < left + split) {
            int index = (int) ((my - lt + tableScroll) / 16);
            if (index >= 0 && index < book().tables.size()) {
                RewardTable table = book().tables.get(index);
                if (button == 1) {
                    ContextMenu m = new ContextMenu();
                    m.add(Component.translatable("corerpg_quests.gui.edit"), "edit", () -> minecraft.setScreen(new PropertyEditorScreen(this, table)));
                    m.add(Component.translatable("corerpg_quests.gui.copy_id"), "copy", () -> minecraft.keyboardHandler.setClipboard(table.id()));
                    m.separator();
                    m.add(Component.translatable("corerpg_quests.gui.delete"), "delete", () -> confirmDelete(table.id(), table.displayTitle())).danger();
                    m.open(font, (int) mx, (int) my, width, height);
                    menu = m;
                } else {
                    selectedId = table.id();
                    entryScroll = 0;
                }
                return true;
            }
            return false;
        }
        RewardTable table = selected();
        int rx = left + split + 8;
        int et = lt + 24;
        if (table != null && my >= et && mx >= rx) {
            int index = (int) ((my - et + entryScroll) / 20);
            if (index >= 0 && index < table.entries.size()) {
                RewardTable.Entry entry = table.entries.get(index);
                if (button == 1) {
                    ContextMenu m = new ContextMenu();
                    m.add(Component.translatable("corerpg_quests.gui.edit"), "edit", () -> minecraft.setScreen(new PropertyEditorScreen(this, entry.reward)));
                    m.separator();
                    m.add(Component.translatable("corerpg_quests.gui.delete"), "delete", () -> EditorActions.delete(entry.reward.id())).danger();
                    m.open(font, (int) mx, (int) my, width, height);
                    menu = m;
                } else {
                    minecraft.setScreen(new PropertyEditorScreen(this, entry.reward));
                }
                return true;
            }
        }
        return false;
    }

    private void confirmDelete(String id, Component name) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                EditorActions.delete(id);
                if (id.equals(selectedId)) {
                    selectedId = null;
                }
            }
            minecraft.setScreen(this);
        }, Component.translatable("corerpg_quests.gui.delete_confirm", name), Component.translatable("corerpg_quests.gui.cannot_undo")));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < left + split) {
            tableScroll = Math.max(0, tableScroll - sy * 16);
        } else {
            entryScroll = Math.max(0, entryScroll - sy * 20);
        }
        return true;
    }
}
