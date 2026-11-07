package dev.nightbeam.corerpg.quests.client.screen;

import dev.nightbeam.corerpg.quests.book.RewardTable;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.widget.ThemedButton;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.reward.types.ItemReward;
import dev.nightbeam.corerpg.quests.reward.types.TableReward;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** "Choose your reward": one card per table entry. */
public class ChoiceScreen extends Screen {
    private static final int CARD = 64;

    private final Screen parent;
    private final TableReward reward;
    private int left;
    private int top;
    private int cols;
    private int rows;
    private int chosen = -1;

    public ChoiceScreen(Screen parent, TableReward reward) {
        super(Component.translatable("corerpg_quests.gui.choose_reward"));
        this.parent = parent;
        this.reward = reward;
    }

    private List<RewardTable.Entry> entries() {
        RewardTable table = reward.table();
        return table == null ? List.of() : table.entries;
    }

    @Override
    protected void init() {
        int n = Math.max(1, entries().size());
        cols = Math.min(n, Math.max(1, (width - 40) / (CARD + 6)));
        rows = (n + cols - 1) / cols;
        int w = cols * (CARD + 6) + 14;
        int h = rows * (CARD + 6) + 56;
        left = (width - w) / 2;
        top = Math.max(8, (height - h) / 2);
        addRenderableWidget(new ThemedButton(left + w - 154, top + h - 22, 70, 16, Component.translatable("corerpg_quests.gui.claim"), b -> confirm(), Theme.GOLD));
        addRenderableWidget(new ThemedButton(left + w - 78, top + h - 22, 70, 16, Component.translatable("gui.cancel"), b -> onClose(), Theme.MUTED));
    }

    private void confirm() {
        if (chosen >= 0) {
            Services.PLATFORM.sendToServer(new Payloads.ClaimReward(reward.id(), chosen));
            onClose();
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
        int w = cols * (CARD + 6) + 14;
        int h = rows * (CARD + 6) + 56;
        Theme.panel(g, left, top, w, h, Theme.GOLD);
        g.drawString(font, title, left + 8, top + 8, Theme.GOLD, false);
        List<RewardTable.Entry> entries = entries();
        List<Component> tip = null;
        for (int i = 0; i < entries.size(); i++) {
            RewardTable.Entry e = entries.get(i);
            int cx = left + 10 + (i % cols) * (CARD + 6);
            int cy = top + 24 + (i / cols) * (CARD + 6);
            boolean hover = mouseX >= cx && mouseX < cx + CARD && mouseY >= cy && mouseY < cy + CARD;
            boolean sel = i == chosen;
            g.fill(cx, cy, cx + CARD, cy + CARD, sel ? 0xFF3A3218 : hover ? 0xFF2A2220 : 0xFF1C1618);
            Theme.outline(g, cx, cy, CARD, CARD, sel ? Theme.GOLD : hover ? Theme.EMBER : Theme.LINE);
            IconRenderer.render(g, e.reward.displayIcon(), cx + CARD / 2f - 12, cy + 8, 24);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(e.reward.displayTitle(), CARD - 6);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                net.minecraft.util.FormattedCharSequence line = lines.get(l);
                g.drawString(font, line, cx + (CARD - font.width(line)) / 2, cy + 38 + l * 10, sel ? Theme.TITLE : Theme.TEXT, false);
            }
            if (hover) {
                tip = new ArrayList<>();
                tip.add(e.reward.displayTitle());
                e.reward.addTooltip(tip);
                if (e.reward instanceof ItemReward ir && !ir.item.isEmpty()) {
                    tip.addAll(Screen.getTooltipFromItem(minecraft, ir.item));
                }
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        List<RewardTable.Entry> entries = entries();
        for (int i = 0; i < entries.size(); i++) {
            int cx = left + 10 + (i % cols) * (CARD + 6);
            int cy = top + 24 + (i / cols) * (CARD + 6);
            if (mx >= cx && mx < cx + CARD && my >= cy && my < cy + CARD) {
                if (chosen == i) {
                    confirm();
                } else {
                    chosen = i;
                }
                return true;
            }
        }
        return false;
    }
}
