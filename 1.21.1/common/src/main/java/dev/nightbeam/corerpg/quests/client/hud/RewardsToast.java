package dev.nightbeam.corerpg.quests.client.hud;

import dev.nightbeam.corerpg.quests.client.render.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** "Rewards received": up to eight item stacks with counts, plus a line of text (XP, stages). */
public class RewardsToast implements Toast {
    private final List<ItemStack> items;
    private final List<Component> lines;

    public RewardsToast(List<ItemStack> items, List<Component> lines) {
        this.items = items;
        this.lines = lines;
    }

    @Override
    public int width() {
        return 176;
    }

    @Override
    public int height() {
        return items.isEmpty() ? 32 : 44;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceLastVisible) {
        Theme.panel(graphics, 0, 0, width(), height(), Theme.GOLD);
        var font = component.getMinecraft().font;
        graphics.drawString(font, Component.translatable("corerpg_quests.toast.rewards"), 8, 6, Theme.GOLD, false);
        int x = 8;
        for (int i = 0; i < Math.min(8, items.size()); i++) {
            ItemStack stack = items.get(i);
            graphics.renderFakeItem(stack, x, 18);
            graphics.renderItemDecorations(font, stack, x, 18);
            x += 20;
        }
        if (!lines.isEmpty()) {
            String text = lines.size() == 1 ? lines.getFirst().getString() : lines.getFirst().getString() + " +" + (lines.size() - 1);
            graphics.drawString(font, font.plainSubstrByWidth(text, width() - 16), 8, items.isEmpty() ? 18 : 36 - 1, Theme.TEXT, false);
        }
        return timeSinceLastVisible >= 5000L * component.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
