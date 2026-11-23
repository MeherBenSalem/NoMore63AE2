package dev.nightbeam.corerpg.quests.client.hud;

import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import dev.nightbeam.corerpg.quests.progress.QuestLogic;
import dev.nightbeam.corerpg.quests.task.Task;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Pinned quests on screen: title, each task with its progress, and a progress bar. */
public final class QuestTracker {
    private static int page;

    private QuestTracker() {
    }

    public static void cycle() {
        page++;
    }

    public static void render(GuiGraphics graphics, Minecraft mc) {
        QuestBook book = ClientQuests.book();
        if (!CoreConfig.CLIENT.trackerEnabled.get() || book == null || !ClientQuests.ready() || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            return;
        }
        List<String> pinned = ClientQuests.progress().pinned(ClientQuests.self());
        if (pinned.isEmpty()) {
            return;
        }
        QuestLogic logic = ClientQuests.logic();
        List<Quest> quests = new ArrayList<>();
        for (String id : pinned) {
            Quest q = book.quest(id);
            if (q != null && logic.isVisible(q) && !(logic.isCompleted(q) && !logic.hasUnclaimedRewards(q, ClientQuests.self()))) {
                quests.add(q);
            }
        }
        if (quests.isEmpty()) {
            return;
        }
        int max = CoreConfig.CLIENT.trackerMaxQuests.get();
        int start = quests.size() <= max ? 0 : (page * max) % quests.size();
        List<Quest> shown = quests.subList(start, Math.min(quests.size(), start + max));

        Font font = mc.font;
        float scale = CoreConfig.CLIENT.trackerScale.get().floatValue();
        int width = 150;
        List<int[]> heights = new ArrayList<>();
        int total = 0;
        for (Quest q : shown) {
            int h = 14 + Math.min(4, q.tasks.size()) * 10 + 6;
            heights.add(new int[]{h});
            total += h + 3;
        }
        int sw = (int) (graphics.guiWidth() / scale);
        int sh = (int) (graphics.guiHeight() / scale);
        CoreConfig.Corner corner = CoreConfig.CLIENT.trackerCorner.get();
        int x = corner == CoreConfig.Corner.TOP_LEFT || corner == CoreConfig.Corner.BOTTOM_LEFT ? 4 : sw - width - 4;
        int y = corner == CoreConfig.Corner.TOP_LEFT || corner == CoreConfig.Corner.TOP_RIGHT ? 4 : sh - total - 4;
        if (corner == CoreConfig.Corner.TOP_LEFT) {
            y += 0;
        }

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        for (int i = 0; i < shown.size(); i++) {
            Quest q = shown.get(i);
            int h = heights.get(i)[0];
            boolean claimable = logic.isCompleted(q);
            Theme.hudPanel(graphics, x, y, width, h, claimable ? Theme.GOLD : Theme.EMBER);
            IconRenderer.renderSmall(graphics, q.displayIcon(), x + 3, y + 3);
            String title = font.plainSubstrByWidth(q.displayTitle().getString(), width - 20);
            graphics.drawString(font, title, x + 15, y + 4, claimable ? Theme.GOLD : Theme.TITLE, false);
            int ty = y + 15;
            int shownTasks = 0;
            for (Task task : q.tasks) {
                if (shownTasks++ >= 4) {
                    break;
                }
                boolean done = logic.isComplete(task);
                Component progress = task.progressText(logic.progress(task));
                String label = (done ? "✔ " : "• ") + task.displayTitle().getString();
                int pw = progress.getString().isEmpty() ? 0 : font.width(progress) + 4;
                graphics.drawString(font, font.plainSubstrByWidth(label, width - 8 - pw), x + 5, ty, done ? Theme.SAGE : Theme.TEXT, false);
                if (pw > 0) {
                    graphics.drawString(font, progress, x + width - pw, ty, Theme.MUTED, false);
                }
                ty += 10;
            }
            int barY = y + h - 4;
            graphics.fill(x + 4, barY, x + width - 4, barY + 2, 0x60000000);
            graphics.fill(x + 4, barY, x + 4 + (int) ((width - 8) * logic.fraction(q)), barY + 2, claimable ? Theme.GOLD : Theme.EMBER);
            y += h + 3;
        }
        graphics.pose().popPose();
    }
}
