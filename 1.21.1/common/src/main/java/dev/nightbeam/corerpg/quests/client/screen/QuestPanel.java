package dev.nightbeam.corerpg.quests.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nightbeam.corerpg.quests.book.AutoclaimMode;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.ObjectKind;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.render.GuiIcons;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.screen.editor.PropertyEditorScreen;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.progress.QuestLogic;
import dev.nightbeam.corerpg.quests.progress.QuestRecord;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.reward.types.ItemReward;
import dev.nightbeam.corerpg.quests.reward.types.TableReward;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.types.ItemCriteriaTask;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The quest window: story text on the left, tasks and rewards on the right. */
final class QuestPanel {
    private static final Pattern IMAGE = Pattern.compile("^\\{image:([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\\s+(\\d+))?(?:\\s+(\\d+))?}$");
    private static final int HEADER = 32;
    private static final int FOOTER = 18;

    private record Hit(int x, int y, int w, int h, @Nullable Runnable left, @Nullable Runnable right, @Nullable List<Component> tooltip,
                       @Nullable ItemStack stack) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** One rendered block of the description: text line or an inline image. */
    private record Block(@Nullable FormattedCharSequence text, @Nullable ResourceLocation image, int width, int height) {
    }

    private final QuestBookScreen screen;
    private Quest quest;
    private int x;
    private int y;
    private int w;
    private int h;
    private int split;
    private int page;
    private double descScroll;
    private double sideScroll;
    private int descContentHeight;
    private int sideContentHeight;
    private final List<Hit> hits = new ArrayList<>();

    QuestPanel(QuestBookScreen screen, Quest quest) {
        this.screen = screen;
        this.quest = quest;
    }

    Quest quest() {
        return quest;
    }

    void rebind(Quest fresh) {
        this.quest = fresh;
    }

    void layout() {
        int sw = screen.width;
        int sh = screen.height;
        w = Math.max(300, Math.min(Math.max(560, quest.effectiveMinWidth()), sw - 40));
        w = Math.min(w, (int) (sw * 0.9));
        h = Math.max(200, Math.min(420, sh - 40));
        x = (sw - w) / 2;
        y = (sh - h) / 2;
        split = (int) (w * 0.56);
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private Font font() {
        return screen.mc().font;
    }

    // ------------------------------------------------------------------ Rendering

    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        QuestLogic logic = screen.logic();
        boolean editing = screen.editing();
        boolean hidden = !editing && logic.isTextHidden(quest);
        QuestLogic.State state = logic.state(quest, ClientQuests.self());
        int accent = switch (state) {
            case COMPLETED -> Theme.SAGE;
            case CLAIMABLE -> Theme.GOLD;
            case IN_PROGRESS -> Theme.EMBER;
            case AVAILABLE -> Theme.SKY;
            case COOLDOWN -> Theme.VIOLET;
            default -> 0xFF8A7E74;
        };
        Theme.panel(g, x, y, w, h, accent);
        renderHeader(g, logic, state, accent, hidden, mouseX, mouseY);
        int bodyTop = y + HEADER;
        int bodyBottom = y + h - FOOTER;
        g.fill(x + split, bodyTop + 4, x + split + 1, bodyBottom - 4, Theme.LINE);
        renderDescription(g, hidden, bodyTop, bodyBottom, mouseX, mouseY);
        renderSide(g, logic, hidden, editing, bodyTop, bodyBottom, mouseX, mouseY);
        renderFooter(g, logic, state, mouseX, mouseY);

        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                if (hit.stack != null && !hit.stack.isEmpty()) {
                    List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(screen.mc(), hit.stack));
                    if (hit.tooltip != null) {
                        lines.addAll(hit.tooltip);
                    }
                    screen.setTooltip(lines);
                } else if (hit.tooltip != null && !hit.tooltip.isEmpty()) {
                    screen.setTooltip(hit.tooltip);
                }
            }
        }
    }

    private void renderHeader(GuiGraphics g, QuestLogic logic, QuestLogic.State state, int accent, boolean hidden, int mouseX, int mouseY) {
        Font font = font();
        IconRenderer.render(g, quest.displayIcon(), x + 8, y + 7, 20);
        int buttonsLeft = x + w - 8;
        buttonsLeft = button(g, buttonsLeft, "close", Component.translatable("gui.done"), Theme.MUTED, mouseX, mouseY, screen::closePanel);
        boolean pinned = ClientQuests.progress().isPinned(ClientQuests.self(), quest.id());
        buttonsLeft = button(g, buttonsLeft, pinned ? "pinned" : "pin", Component.translatable(pinned ? "corerpg_quests.gui.unpin" : "corerpg_quests.gui.pin"),
                pinned ? Theme.EMBER : Theme.MUTED, mouseX, mouseY, () -> Services.PLATFORM.sendToServer(new Payloads.PinQuest(quest.id(), !pinned)));
        if (!quest.guidePage.isBlank()) {
            String url = quest.guidePage;
            buttonsLeft = button(g, buttonsLeft, "info", Component.translatable("corerpg_quests.gui.guide_page"), Theme.SKY, mouseX, mouseY, () -> openUrl(url));
        }
        if (screen.editing()) {
            buttonsLeft = button(g, buttonsLeft, "edit", Component.translatable("corerpg_quests.gui.edit_quest"), Theme.GOLD, mouseX, mouseY,
                    () -> screen.mc().setScreen(new PropertyEditorScreen(screen, quest)));
            buttonsLeft = button(g, buttonsLeft, "add", Component.translatable("corerpg_quests.gui.add_task"), Theme.GOLD, mouseX, mouseY, this::addTaskMenu);
            buttonsLeft = button(g, buttonsLeft, "gift", Component.translatable("corerpg_quests.gui.add_reward"), Theme.GOLD, mouseX, mouseY, this::addRewardMenu);
        }
        int titleWidth = buttonsLeft - x - 36;
        Component title = Theme.display(hidden ? Component.literal("???") : quest.displayTitle());
        Theme.drawFitted(g, font, title, x + 34, y + 7, titleWidth, Theme.TITLE);
        Component sub;
        if (!hidden && !quest.subtitle.isBlank()) {
            sub = TextUtil.format(quest.subtitle);
        } else {
            sub = Component.literal(quest.chapter().displayTitle().getString());
        }
        g.drawString(font, font.substrByWidth(sub, titleWidth).getString(), x + 34, y + 19, Theme.MUTED, false);
        Theme.separator(g, x + 6, y + HEADER - 2, w - 12, Theme.withAlpha(accent, 0x90));
    }

    private int button(GuiGraphics g, int right, String icon, Component tooltip, int color, int mouseX, int mouseY, Runnable action) {
        int bx = right - 14;
        int by = y + 9;
        boolean hover = mouseX >= bx - 1 && mouseX < bx + 15 && mouseY >= by - 1 && mouseY < by + 15;
        if (hover) {
            g.fill(bx - 1, by - 1, bx + 15, by + 15, 0x40E8743B);
        }
        GuiIcons.draw(g, icon, bx, by, 14, hover ? Theme.TITLE : color);
        hits.add(new Hit(bx - 1, by - 1, 16, 16, action, null, List.of(tooltip), null));
        return bx - 4;
    }

    private void renderDescription(GuiGraphics g, boolean hidden, int top, int bottom, int mouseX, int mouseY) {
        Font font = font();
        int left = x + 10;
        int width = split - 18;
        List<List<String>> pages = pages();
        page = Math.max(0, Math.min(page, pages.size() - 1));
        List<Block> blocks = new ArrayList<>();
        if (hidden) {
            blocks.add(new Block(Component.translatable("corerpg_quests.gui.text_hidden").withStyle(ChatFormatting.ITALIC).getVisualOrderText(), null, 0, 10));
        } else {
            for (String raw : pages.get(page)) {
                Matcher m = IMAGE.matcher(raw.trim());
                if (m.matches()) {
                    ResourceLocation tex = ResourceLocation.tryParse(m.group(1));
                    int iw = m.group(2) != null ? Integer.parseInt(m.group(2)) : 64;
                    int ih = m.group(3) != null ? Integer.parseInt(m.group(3)) : iw;
                    if (iw > width) {
                        ih = ih * width / iw;
                        iw = width;
                    }
                    if (tex != null) {
                        blocks.add(new Block(null, tex, iw, ih + 4));
                    }
                    continue;
                }
                if (raw.isBlank()) {
                    blocks.add(new Block(FormattedCharSequence.EMPTY, null, 0, 6));
                    continue;
                }
                for (FormattedCharSequence line : font.split(TextUtil.format(raw), width)) {
                    blocks.add(new Block(line, null, 0, 10));
                }
            }
        }
        boolean multiPage = pages.size() > 1;
        int viewBottom = bottom - (multiPage ? 16 : 4);
        descContentHeight = 0;
        for (Block b : blocks) {
            descContentHeight += b.height;
        }
        int depsHeight = dependencyBlockHeight();
        descContentHeight += depsHeight;
        int view = viewBottom - top - 6;
        descScroll = Math.max(0, Math.min(descScroll, descContentHeight - view));
        g.enableScissor(x + 2, top + 2, x + split - 2, viewBottom);
        int cy = top + 6 - (int) descScroll;
        for (Block b : blocks) {
            if (b.image != null) {
                RenderSystem.enableBlend();
                g.blit(b.image, left + (width - b.width) / 2, cy, b.width, b.height - 4, 0, 0, 1, 1, 1, 1);
                RenderSystem.disableBlend();
            } else if (b.text != null) {
                g.drawString(font, b.text, left, cy, Theme.TEXT, false);
            }
            cy += b.height;
        }
        renderDependencies(g, left, cy, width, top, viewBottom);
        g.disableScissor();
        if (descContentHeight > view) {
            int barH = Math.max(10, view * view / descContentHeight);
            int barY = top + 4 + (int) ((view - barH) * descScroll / Math.max(1, descContentHeight - view));
            g.fill(x + split - 4, barY, x + split - 2, barY + barH, Theme.LINE);
        }
        if (multiPage) {
            String label = (page + 1) + " / " + pages.size();
            int lw = font.width(label);
            int cx = x + split / 2;
            g.drawString(font, label, cx - lw / 2, bottom - 12, Theme.MUTED, false);
            if (page > 0) {
                GuiIcons.draw(g, "arrow_left", cx - lw / 2 - 16, bottom - 14, 12, Theme.TEXT);
                hits.add(new Hit(cx - lw / 2 - 18, bottom - 15, 16, 14, () -> {
                    page--;
                    descScroll = 0;
                }, null, null, null));
            }
            if (page < pages.size() - 1) {
                GuiIcons.draw(g, "arrow_right", cx + lw / 2 + 4, bottom - 14, 12, Theme.TEXT);
                hits.add(new Hit(cx + lw / 2 + 2, bottom - 15, 16, 14, () -> {
                    page++;
                    descScroll = 0;
                }, null, null, null));
            }
        }
    }

    private List<List<String>> pages() {
        List<List<String>> pages = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : quest.description) {
            if (line.trim().equals(TextUtil.PAGE_BREAK)) {
                pages.add(current);
                current = new ArrayList<>();
            } else {
                current.add(line);
            }
        }
        pages.add(current);
        if (pages.size() == 1 && pages.getFirst().isEmpty()) {
            pages.getFirst().add("");
        }
        return pages;
    }

    private List<QuestObject> dependencyObjects() {
        List<QuestObject> deps = new ArrayList<>();
        for (String id : quest.dependencies) {
            QuestObject o = screen.book().get(id);
            if (o instanceof Quest || o instanceof Chapter) {
                deps.add(o);
            }
        }
        return deps;
    }

    private int dependencyBlockHeight() {
        List<QuestObject> deps = dependencyObjects();
        List<Quest> unlocks = screen.book().dependents(quest.id());
        int hgt = 0;
        if (!deps.isEmpty()) {
            hgt += 16 + deps.size() * 12;
        }
        if (!unlocks.isEmpty()) {
            hgt += 16 + Math.min(unlocks.size(), 6) * 12;
        }
        return hgt;
    }

    private void renderDependencies(GuiGraphics g, int left, int cy, int width, int top, int bottom) {
        Font font = font();
        QuestLogic logic = screen.logic();
        boolean editing = screen.editing();
        List<QuestObject> deps = dependencyObjects();
        if (!deps.isEmpty()) {
            cy += 6;
            Theme.separator(g, left, cy, width, Theme.LINE);
            cy += 4;
            Component header = switch (quest.dependencyMode) {
                case ALL_COMPLETED -> Component.translatable(quest.minRequiredDependencies > 0 ? "corerpg_quests.gui.requires_n" : "corerpg_quests.gui.requires_all", quest.minRequiredDependencies);
                case ONE_COMPLETED -> Component.translatable("corerpg_quests.gui.requires_one");
                case ALL_STARTED -> Component.translatable("corerpg_quests.gui.requires_all_started");
                case ONE_STARTED -> Component.translatable("corerpg_quests.gui.requires_one_started");
            };
            g.drawString(font, header, left, cy, Theme.MUTED, false);
            cy += 12;
            for (QuestObject dep : deps) {
                boolean done = dep instanceof Quest q ? logic.isCompleted(q) : logic.isChapterComplete((Chapter) dep);
                boolean visible = editing || !(dep instanceof Quest q) || logic.isVisible(q);
                Component name = visible ? dep.displayTitle() : Component.literal("???");
                GuiIcons.draw(g, done ? "check" : "lock", left, cy, 9, done ? Theme.SAGE : Theme.FAINT);
                g.drawString(font, font.substrByWidth(name, width - 14).getString(), left + 12, cy + 1, done ? Theme.SAGE : Theme.TEXT, false);
                if (visible && cy >= top && cy < bottom) {
                    hits.add(new Hit(left, cy, width, 11, () -> screen.focus(dep.id()), null, null, null));
                }
                cy += 12;
            }
        }
        List<Quest> unlocks = screen.book().dependents(quest.id());
        if (!unlocks.isEmpty()) {
            cy += 4;
            g.drawString(font, Component.translatable("corerpg_quests.gui.unlocks"), left, cy, Theme.MUTED, false);
            cy += 12;
            int shown = 0;
            for (Quest q : unlocks) {
                if (shown++ >= 6) {
                    break;
                }
                boolean visible = editing || logic.isVisible(q);
                GuiIcons.draw(g, "arrow_right", left, cy, 9, Theme.FAINT);
                g.drawString(font, font.substrByWidth(visible ? q.displayTitle() : Component.literal("???"), width - 14).getString(), left + 12, cy + 1, Theme.MUTED, false);
                if (visible && cy >= top && cy < bottom) {
                    hits.add(new Hit(left, cy, width, 11, () -> screen.focus(q.id()), null, null, null));
                }
                cy += 12;
            }
        }
    }

    private void renderSide(GuiGraphics g, QuestLogic logic, boolean hidden, boolean editing, int top, int bottom, int mouseX, int mouseY) {
        Font font = font();
        int left = x + split + 8;
        int width = w - split - 16;
        sideContentHeight = sideHeight(editing);
        int view = bottom - top - 6;
        sideScroll = Math.max(0, Math.min(sideScroll, sideContentHeight - view));
        g.enableScissor(x + split + 2, top + 2, x + w - 2, bottom - 2);
        int cy = top + 6 - (int) sideScroll;

        g.drawString(font, Theme.display(Component.translatable("corerpg_quests.gui.tasks")), left, cy, Theme.GOLD, false);
        String count = logic.completedTaskCount(quest) + "/" + quest.tasks.size();
        g.drawString(font, count, left + width - font.width(count), cy, Theme.MUTED, false);
        cy += 13;
        if (quest.tasks.isEmpty()) {
            g.drawString(font, Component.translatable("corerpg_quests.gui.no_tasks"), left, cy, Theme.FAINT, false);
            cy += 12;
        }
        int index = 0;
        for (Task task : quest.tasks) {
            renderTask(g, logic, task, index++, hidden, editing, left, cy, width, top, bottom, mouseX, mouseY);
            cy += 24;
        }
        cy += 6;
        g.drawString(font, Theme.display(Component.translatable("corerpg_quests.gui.rewards")), left, cy, Theme.GOLD, false);
        cy += 13;
        List<Reward> rewards = visibleRewards(editing);
        if (rewards.isEmpty()) {
            g.drawString(font, Component.translatable("corerpg_quests.gui.no_rewards"), left, cy, Theme.FAINT, false);
        }
        index = 0;
        for (Reward reward : rewards) {
            renderReward(g, logic, reward, index++, editing, left, cy, width, top, bottom, mouseX, mouseY);
            cy += 22;
        }
        g.disableScissor();
        if (sideContentHeight > view) {
            int barH = Math.max(10, view * view / sideContentHeight);
            int barY = top + 4 + (int) ((view - barH) * sideScroll / Math.max(1, sideContentHeight - view));
            g.fill(x + w - 5, barY, x + w - 3, barY + barH, Theme.LINE);
        }
    }

    private List<Reward> visibleRewards(boolean editing) {
        List<Reward> list = new ArrayList<>();
        for (Reward r : quest.rewards) {
            if (editing || r.effectiveAutoclaim() != AutoclaimMode.INVISIBLE) {
                list.add(r);
            }
        }
        return list;
    }

    private int sideHeight(boolean editing) {
        return 13 + Math.max(12, quest.tasks.size() * 24) + 6 + 13 + Math.max(12, visibleRewards(editing).size() * 22) + 8;
    }

    private void renderTask(GuiGraphics g, QuestLogic logic, Task task, int index, boolean hidden, boolean editing, int left, int cy, int width,
                            int top, int bottom, int mouseX, int mouseY) {
        Font font = font();
        boolean done = logic.isComplete(task);
        boolean canProgress = logic.canProgress(task);
        boolean hover = mouseX >= left && mouseX < left + width && mouseY >= cy && mouseY < cy + 22 && mouseY >= top && mouseY < bottom;
        g.fill(left, cy, left + width, cy + 22, hover ? 0x30FFFFFF : 0x18FFFFFF);
        if (done) {
            g.fill(left, cy, left + 2, cy + 22, Theme.SAGE);
        } else if (canProgress) {
            g.fill(left, cy, left + 2, cy + 22, Theme.EMBER);
        }
        IconRenderer.render(g, task.displayIcon(), left + 4, cy + 3, 16);
        int textRight = left + width - 4;
        boolean showSubmit = !done && task.canSubmit() && canProgress && !hidden;
        Component submit = task.submitLabel();
        int bw = font.width(submit) + 8;
        if (showSubmit) {
            int bx = textRight - bw;
            int by = cy + 5;
            boolean bh = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 12;
            g.fill(bx, by, bx + bw, by + 12, bh ? 0xFFB8562A : 0xFF8A3E1E);
            Theme.outline(g, bx, by, bw, 12, Theme.EMBER);
            g.drawString(font, submit, bx + 4, by + 2, Theme.TITLE, false);
            if (by >= top && by < bottom) {
                hits.add(new Hit(bx, by, bw, 12, () -> {
                    screen.click();
                    Services.PLATFORM.sendToServer(new Payloads.SubmitTask(task.id()));
                }, null, null, null));
            }
            textRight = bx - 4;
        } else if (done) {
            GuiIcons.draw(g, "check", textRight - 11, cy + 6, 11, Theme.SAGE);
            textRight -= 14;
        }
        Component title = hidden ? Component.literal("???") : task.displayTitle();
        if (task.optionalTask) {
            title = title.copy().append(Component.literal(" *").withStyle(ChatFormatting.DARK_PURPLE));
        }
        g.drawString(font, font.substrByWidth(title, textRight - left - 24).getString(), left + 23, cy + 3, done ? Theme.SAGE : Theme.TEXT, false);
        long max = task.maxProgress();
        if (max > 1 && !hidden) {
            Component progressText = task.progressText(logic.progress(task));
            int pw = font.width(progressText);
            int barW = Math.max(10, textRight - left - 28 - pw);
            Theme.bar(g, left + 23, cy + 15, barW, 3, logic.fraction(task), done ? Theme.SAGE : Theme.EMBER);
            g.drawString(font, progressText, left + 27 + barW, cy + 13, Theme.MUTED, false);
        } else if (!done && !canProgress && !hidden) {
            g.drawString(font, Component.translatable("corerpg_quests.gui.task_locked"), left + 23, cy + 13, Theme.FAINT, false);
        }
        if (cy + 22 > top && cy < bottom) {
            List<Component> tip = new ArrayList<>();
            tip.add(hidden ? Component.literal("???") : task.displayTitle());
            if (!hidden) {
                task.addTooltip(tip);
            }
            if (editing) {
                tip.add(Component.literal(task.type().id() + "  " + task.id()).withStyle(ChatFormatting.DARK_GRAY));
                tip.add(Component.translatable("corerpg_quests.gui.row_menu_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            ItemStack stack = !hidden && task instanceof ItemCriteriaTask it && it.filter.isBlank() ? it.item : null;
            hits.add(new Hit(left, cy, Math.max(0, textRight - left), 22, editing ? () -> edit(task) : null,
                    editing ? () -> rowMenu(task, index, quest.tasks.size()) : null, tip, stack));
        }
    }

    private void renderReward(GuiGraphics g, QuestLogic logic, Reward reward, int index, boolean editing, int left, int cy, int width,
                              int top, int bottom, int mouseX, int mouseY) {
        Font font = font();
        boolean claimed = logic.isClaimed(reward, ClientQuests.self());
        boolean claimable = logic.canClaim(reward, ClientQuests.self());
        boolean hover = mouseX >= left && mouseX < left + width && mouseY >= cy && mouseY < cy + 20 && mouseY >= top && mouseY < bottom;
        g.fill(left, cy, left + width, cy + 20, hover ? 0x30FFFFFF : 0x18FFFFFF);
        if (claimable) {
            g.fill(left, cy, left + 2, cy + 20, Theme.GOLD);
        }
        IconRenderer.render(g, reward.displayIcon(), left + 4, cy + 2, 16);
        int textRight = left + width - 4;
        if (claimable) {
            Component label = Component.translatable(reward.needsChoice() ? "corerpg_quests.gui.choose" : "corerpg_quests.gui.claim");
            int bw = font.width(label) + 8;
            int bx = textRight - bw;
            int by = cy + 4;
            boolean bh = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 12;
            g.fill(bx, by, bx + bw, by + 12, bh ? 0xFFB08A2A : 0xFF7A5E1A);
            Theme.outline(g, bx, by, bw, 12, Theme.GOLD);
            g.drawString(font, label, bx + 4, by + 2, Theme.TITLE, false);
            if (by >= top && by < bottom) {
                hits.add(new Hit(bx, by, bw, 12, () -> claim(reward), null, null, null));
            }
            textRight = bx - 4;
        } else if (claimed) {
            GuiIcons.draw(g, "check", textRight - 11, cy + 5, 11, Theme.SAGE);
            textRight -= 14;
        }
        Component title = reward.displayTitle();
        if (reward.isTeamReward()) {
            title = title.copy().append(Component.literal(" ⚑").withStyle(ChatFormatting.AQUA));
        }
        int color = claimed ? Theme.MUTED : claimable ? Theme.GOLD : Theme.TEXT;
        g.drawString(font, font.substrByWidth(title, textRight - left - 24).getString(), left + 23, cy + 6, color, false);
        if (cy + 20 > top && cy < bottom) {
            List<Component> tip = new ArrayList<>();
            tip.add(reward.displayTitle());
            reward.addTooltip(tip);
            if (reward.isTeamReward()) {
                tip.add(Component.translatable("corerpg_quests.gui.team_reward").withStyle(ChatFormatting.AQUA));
            }
            if (reward.excludeFromClaimAll) {
                tip.add(Component.translatable("corerpg_quests.gui.excluded_from_claim_all").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (editing) {
                tip.add(Component.literal(reward.type().id() + "  " + reward.id() + "  autoclaim=" + reward.effectiveAutoclaim().getSerializedName()).withStyle(ChatFormatting.DARK_GRAY));
                tip.add(Component.translatable("corerpg_quests.gui.row_menu_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            ItemStack stack = reward instanceof ItemReward ir ? ir.item : null;
            hits.add(new Hit(left, cy, Math.max(0, textRight - left), 20, editing ? () -> edit(reward) : null,
                    editing ? () -> rowMenu(reward, index, quest.rewards.size()) : null, tip, stack));
        }
    }

    private void renderFooter(GuiGraphics g, QuestLogic logic, QuestLogic.State state, int mouseX, int mouseY) {
        Font font = font();
        int fy = y + h - FOOTER + 4;
        Theme.separator(g, x + 6, fy - 4, w - 12, Theme.LINE);
        List<String> bits = new ArrayList<>();
        Component status = switch (state) {
            case LOCKED -> Component.translatable("corerpg_quests.state.locked");
            case EXCLUDED -> Component.translatable("corerpg_quests.state.excluded");
            case COOLDOWN -> Component.translatable("corerpg_quests.state.cooldown", TextUtil.duration(logic.cooldownRemainingMillis(quest) / 1000));
            case AVAILABLE -> Component.translatable("corerpg_quests.state.available");
            case IN_PROGRESS -> Component.translatable("corerpg_quests.state.in_progress", (int) Math.floor(logic.fraction(quest) * 100));
            case CLAIMABLE -> Component.translatable("corerpg_quests.state.claimable");
            case COMPLETED -> Component.translatable("corerpg_quests.state.completed");
        };
        bits.add(status.getString());
        QuestRecord record = ClientQuests.progress().record(quest.id());
        if (quest.repeatable) {
            bits.add(Component.translatable("corerpg_quests.gui.times_completed", record == null ? 0 : record.timesCompleted).getString());
        }
        if (quest.optional) {
            bits.add(Component.translatable("corerpg_quests.state.optional").getString());
        }
        if (ClientQuests.progress().locked) {
            bits.add(Component.translatable("corerpg_quests.gui.progress_locked").getString());
        }
        g.drawString(font, font.plainSubstrByWidth(String.join("  ·  ", bits), w - 120), x + 10, fy, Theme.MUTED, false);
        int claimable = 0;
        for (Reward r : quest.rewards) {
            if (!r.needsChoice() && logic.canClaim(r, ClientQuests.self())) {
                claimable++;
            }
        }
        if (claimable > 1) {
            Component label = Component.translatable("corerpg_quests.gui.claim_all");
            int bw = font.width(label) + 10;
            int bx = x + w - 8 - bw;
            boolean bh = mouseX >= bx && mouseX < bx + bw && mouseY >= fy - 2 && mouseY < fy + 10;
            g.fill(bx, fy - 2, bx + bw, fy + 10, bh ? 0xFFB08A2A : 0xFF7A5E1A);
            Theme.outline(g, bx, fy - 2, bw, 12, Theme.GOLD);
            g.drawString(font, label, bx + 5, fy, Theme.TITLE, false);
            hits.add(new Hit(bx, fy - 2, bw, 12, () -> {
                screen.click();
                Services.PLATFORM.sendToServer(new Payloads.ClaimAll(quest.id()));
            }, null, null, null));
        }
    }

    // ------------------------------------------------------------------ Actions

    private void claim(Reward reward) {
        screen.click();
        if (reward.needsChoice() && reward instanceof TableReward tr) {
            screen.mc().setScreen(new ChoiceScreen(screen, tr));
        } else {
            Services.PLATFORM.sendToServer(new Payloads.ClaimReward(reward.id(), -1));
        }
    }

    private void edit(QuestObject object) {
        screen.mc().setScreen(new PropertyEditorScreen(screen, object));
    }

    private void rowMenu(QuestObject object, int index, int size) {
        ContextMenu m = new ContextMenu();
        m.add(Component.translatable("corerpg_quests.gui.edit"), "edit", () -> edit(object));
        m.add(Component.translatable("corerpg_quests.gui.move_up"), "arrow_left", () -> EditorActions.reorder(object.id(), index - 1)).disabled(index <= 0);
        m.add(Component.translatable("corerpg_quests.gui.move_down"), "arrow_right", () -> EditorActions.reorder(object.id(), index + 1)).disabled(index >= size - 1);
        m.add(Component.translatable("corerpg_quests.gui.copy_id"), "copy", () -> screen.mc().keyboardHandler.setClipboard(object.id()));
        m.separator();
        m.add(Component.translatable("corerpg_quests.gui.delete"), "delete", () -> screen.confirmDelete(object)).danger();
        Minecraft mc = screen.mc();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        screen.openMenu(m, (int) mx, (int) my);
    }

    private void addTaskMenu() {
        ContextMenu m = new ContextMenu();
        for (TaskType<?> type : TaskTypes.all()) {
            m.add(Component.translatable(type.translationKey()), null, () -> {
                com.google.gson.JsonObject data = new com.google.gson.JsonObject();
                data.addProperty("type", type.shortId());
                EditorActions.focusNextCreated("edit");
                EditorActions.create(ObjectKind.TASK, quest.id(), data);
            });
        }
        screen.openMenu(m, x + w - 120, y + 26);
    }

    private void addRewardMenu() {
        ContextMenu m = new ContextMenu();
        for (RewardType<?> type : RewardTypes.all()) {
            m.add(Component.translatable(type.translationKey()), null, () -> {
                com.google.gson.JsonObject data = new com.google.gson.JsonObject();
                data.addProperty("type", type.shortId());
                EditorActions.focusNextCreated("edit");
                EditorActions.create(ObjectKind.REWARD, quest.id(), data);
            });
        }
        screen.openMenu(m, x + w - 120, y + 26);
    }

    private void openUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return;
        }
        Minecraft mc = screen.mc();
        mc.setScreen(new ConfirmLinkScreen(yes -> {
            if (yes) {
                Util.getPlatform().openUri(url);
            }
            mc.setScreen(screen);
        }, url, true));
    }

    // ------------------------------------------------------------------ Input

    boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) {
            return false;
        }
        for (Hit hit : List.copyOf(hits)) {
            if (hit.contains(mx, my)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit.left != null) {
                    hit.left.run();
                    return true;
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit.right != null) {
                    hit.right.run();
                    return true;
                }
            }
        }
        return true;
    }

    boolean mouseReleased(double mx, double my, int button) {
        return contains(mx, my);
    }

    boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return contains(mx, my);
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (mx < x + split) {
            descScroll = Math.max(0, descScroll - amount * 12);
        } else {
            sideScroll = Math.max(0, sideScroll - amount * 12);
        }
        return true;
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT && page > 0) {
            page--;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            page++;
            return true;
        }
        if (key == GLFW.GLFW_KEY_E && screen.editing()) {
            edit(quest);
            return true;
        }
        return false;
    }

    static boolean isTooltipFlagAdvanced() {
        return Minecraft.getInstance().options.advancedItemTooltips;
    }

    static TooltipFlag flag() {
        return isTooltipFlagAdvanced() ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
    }

    static QuestBook book() {
        return ClientQuests.book();
    }
}
