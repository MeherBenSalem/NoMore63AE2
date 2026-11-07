package dev.nightbeam.corerpg.quests.client.hud;

import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.network.Payloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Quest, chapter and custom toasts, drawn in the quest book's ember-and-ash style. */
public class QuestToast implements Toast {
    private final Payloads.NotifyKind kind;
    private final Component title;
    private final Component text;
    private final ItemStack icon;

    public QuestToast(Payloads.NotifyKind kind, Component title, Component text, ItemStack icon) {
        this.kind = kind;
        this.title = title;
        this.text = text;
        this.icon = icon;
    }

    @Override
    public int width() {
        return 176;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceLastVisible) {
        int accent = switch (kind) {
            case CHAPTER_COMPLETED, BOOK_COMPLETED -> Theme.GOLD;
            case TASK_COMPLETED -> Theme.SAGE;
            case QUEST_UNLOCKED -> Theme.SKY;
            default -> Theme.EMBER;
        };
        Theme.panel(graphics, 0, 0, width(), height(), accent);
        if (!icon.isEmpty()) {
            graphics.renderFakeItem(icon, 8, 8);
        }
        var font = component.getMinecraft().font;
        Component top = kind == Payloads.NotifyKind.TOAST ? title : text;
        Component bottom = kind == Payloads.NotifyKind.TOAST ? text : title;
        Theme.drawFitted(graphics, font, Theme.display(top), 30, 6, width() - 36, accent);
        graphics.drawString(font, font.substrByWidth(bottom, width() - 36).getString(), 30, 18, Theme.TEXT, false);
        long shown = kind == Payloads.NotifyKind.BOOK_COMPLETED ? 9000L : 5000L;
        return timeSinceLastVisible >= shown * component.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
