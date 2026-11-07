package dev.nightbeam.corerpg.quests.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.ChapterImage;
import dev.nightbeam.corerpg.quests.book.ClickAction;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.book.ObjectKind;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.QuestLink;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.book.QuestShape;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.render.GuiIcons;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.ShapeRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.screen.editor.PickerScreen;
import dev.nightbeam.corerpg.quests.client.screen.editor.PropertyEditorScreen;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.progress.QuestLogic;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.util.Ids;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The chapter canvas: quests, links and images on a pannable, zoomable plane, and every editor gesture on it. */
final class CanvasView {
    private static final float UNIT = 24f;
    private static final float NODE = 22f;
    private static final String CLIPBOARD_KEY = "corerpg_quests_clipboard";

    private enum Drag { NONE, PAN, MOVE, BOX }

    private final QuestBookScreen screen;
    private @Nullable Chapter chapter;
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private final Set<String> selection = new LinkedHashSet<>();

    private Drag drag = Drag.NONE;
    private double pressX;
    private double pressY;
    private boolean dragMoved;
    private @Nullable QuestObject pressed;
    private double moveX;
    private double moveY;
    private double pendingMoveX;
    private double pendingMoveY;
    private Set<String> pendingMoveIds = Set.of();
    private int pendingMoveVersion = -1;
    private double boxX;
    private double boxY;
    private double mouseUnitX;
    private double mouseUnitY;

    CanvasView(QuestBookScreen screen) {
        this.screen = screen;
    }

    // ------------------------------------------------------------------ View

    void reset(@Nullable Chapter chapter) {
        this.chapter = chapter;
        selection.clear();
        drag = Drag.NONE;
        if (chapter == null) {
            panX = panY = 0;
            zoom = 1;
            return;
        }
        Quest focus = chapter.autofocus.isEmpty() ? null : chapter.book().quest(chapter.autofocus);
        List<double[]> points = new ArrayList<>();
        QuestLogic logic = screen.logic();
        for (Quest q : chapter.quests) {
            if (screen.editing() || logic.isVisible(q)) {
                points.add(new double[]{q.x, q.y, q.effectiveSize()});
            }
        }
        for (QuestLink l : chapter.links) {
            points.add(new double[]{l.x, l.y, l.effectiveSize()});
        }
        if (points.isEmpty()) {
            panX = panY = 0;
            zoom = 1;
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : points) {
            minX = Math.min(minX, p[0] - p[2] / 2);
            maxX = Math.max(maxX, p[0] + p[2] / 2);
            minY = Math.min(minY, p[1] - p[2] / 2);
            maxY = Math.max(maxY, p[1] + p[2] / 2);
        }
        double w = Math.max(1, screen.canvasRight() - screen.canvasLeft() - 40);
        double h = Math.max(1, screen.canvasBottom() - screen.canvasTop() - 40);
        double fit = Math.min(w / ((maxX - minX + 1) * UNIT), h / ((maxY - minY + 1) * UNIT));
        zoom = Math.max(0.5, Math.min(1.6, fit));
        if (focus != null && fit < 0.5) {
            // The chapter is too big to fit: start at the author's chosen quest.
            panX = focus.x;
            panY = focus.y;
        } else {
            panX = (minX + maxX) / 2;
            panY = (minY + maxY) / 2;
        }
    }

    void rebind(Chapter chapter) {
        this.chapter = chapter;
        selection.removeIf(id -> chapter.book().get(id) == null);
    }

    void centerOn(double x, double y) {
        panX = x;
        panY = y;
    }

    void clampView() {
        zoom = Math.max(0.25, Math.min(4, zoom));
    }

    private double scale() {
        return UNIT * zoom;
    }

    private float sx(double x) {
        return (float) ((screen.canvasLeft() + screen.canvasRight()) / 2.0 + (x - panX) * scale());
    }

    private float sy(double y) {
        return (float) ((screen.canvasTop() + screen.canvasBottom()) / 2.0 + (y - panY) * scale());
    }

    private double ux(double mx) {
        return panX + (mx - (screen.canvasLeft() + screen.canvasRight()) / 2.0) / scale();
    }

    private double uy(double my) {
        return panY + (my - (screen.canvasTop() + screen.canvasBottom()) / 2.0) / scale();
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= screen.canvasLeft() && mx < screen.canvasRight() && my >= screen.canvasTop() && my < screen.canvasBottom();
    }

    String statusText() {
        String text = String.format(Locale.ROOT, "%d%%  ·  %.1f, %.1f", Math.round(zoom * 100), mouseUnitX, mouseUnitY);
        if (!selection.isEmpty()) {
            text = Component.translatable("corerpg_quests.gui.selected", selection.size()).getString() + "  ·  " + text;
        }
        return text;
    }

    // ------------------------------------------------------------------ Positions with move preview

    private double objX(QuestObject o) {
        double base = switch (o) {
            case Quest q -> q.x;
            case QuestLink l -> l.x;
            case ChapterImage i -> i.x;
            default -> 0;
        };
        return base + offsetFor(o, true);
    }

    private double objY(QuestObject o) {
        double base = switch (o) {
            case Quest q -> q.y;
            case QuestLink l -> l.y;
            case ChapterImage i -> i.y;
            default -> 0;
        };
        return base + offsetFor(o, false);
    }

    private double offsetFor(QuestObject o, boolean x) {
        double offset = 0;
        if (drag == Drag.MOVE && dragMoved && selection.contains(o.id())) {
            offset += x ? snap(moveX) : snap(moveY);
        }
        if (pendingMoveIds.contains(o.id())) {
            if (ClientQuests.version() != pendingMoveVersion) {
                pendingMoveIds = Set.of();
            } else {
                offset += x ? pendingMoveX : pendingMoveY;
            }
        }
        return offset;
    }

    private double snap(double value) {
        double grid = Math.max(0.0625, screen.book().gridScale);
        return Math.round(value / grid) * grid;
    }

    // ------------------------------------------------------------------ Rendering

    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = screen.canvasLeft();
        int top = screen.canvasTop();
        int right = screen.canvasRight();
        int bottom = screen.canvasBottom();
        if (inCanvas(mouseX, mouseY)) {
            mouseUnitX = ux(mouseX);
            mouseUnitY = uy(mouseY);
        }
        g.enableScissor(left, top, right, bottom);
        renderGrid(g, left, top, right, bottom);
        if (chapter == null) {
            g.disableScissor();
            return;
        }
        QuestLogic logic = screen.logic();
        boolean editing = screen.editing();
        long time = Util.getMillis();

        List<ChapterImage> images = new ArrayList<>(chapter.images);
        images.sort(Comparator.comparingInt(i -> i.order));
        for (ChapterImage image : images) {
            if (image.order < 0) {
                renderImage(g, image, editing);
            }
        }
        renderLines(g, logic, editing, time);
        QuestObject hovered = inCanvas(mouseX, mouseY) ? objectAt(mouseX, mouseY) : null;
        for (QuestLink link : chapter.links) {
            renderLink(g, link, logic, editing, link == hovered);
        }
        for (Quest quest : chapter.quests) {
            if (editing || logic.isVisible(quest)) {
                renderQuest(g, quest, logic, editing, quest == hovered, time);
            }
        }
        for (ChapterImage image : images) {
            if (image.order >= 0) {
                renderImage(g, image, editing);
            }
        }
        if (drag == Drag.BOX) {
            int x1 = (int) Math.min(boxX, mouseX), y1 = (int) Math.min(boxY, mouseY);
            int x2 = (int) Math.max(boxX, mouseX), y2 = (int) Math.max(boxY, mouseY);
            g.fill(x1, y1, x2, y2, 0x30E8C15A);
            Theme.outline(g, x1, y1, x2 - x1, y2 - y1, Theme.GOLD);
        }
        g.disableScissor();
        if (hovered != null && drag != Drag.MOVE) {
            screen.setTooltip(tooltip(hovered, logic, editing));
        }
        if (chapter.quests.isEmpty() && chapter.links.isEmpty() && editing) {
            Component hint = Component.translatable("corerpg_quests.gui.empty_chapter_hint");
            int w = screen.mc().font.width(hint);
            g.drawString(screen.mc().font, hint, (left + right - w) / 2, (top + bottom) / 2, Theme.MUTED, false);
        }
    }

    private void renderGrid(GuiGraphics g, int left, int top, int right, int bottom) {
        double grid = screen.editing() ? Math.max(0.5, screen.book().gridScale) : 1;
        double step = grid * scale();
        if (step < 8) {
            step *= Math.ceil(8 / step);
        }
        float ox = sx(0);
        float oy = sy(0);
        double startX = ox - Math.ceil((ox - left) / step) * step;
        double startY = oy - Math.ceil((oy - top) / step) * step;
        int color = screen.editing() ? 0x22E8C15A : 0x10FFFFFF;
        for (double x = startX; x < right; x += step) {
            for (double y = startY; y < bottom; y += step) {
                g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, color);
            }
        }
        if (screen.editing()) {
            g.fill((int) ox - 3, (int) oy, (int) ox + 4, (int) oy + 1, 0x40E8743B);
            g.fill((int) ox, (int) oy - 3, (int) ox + 1, (int) oy + 4, 0x40E8743B);
        }
    }

    private void renderLines(GuiGraphics g, QuestLogic logic, boolean editing, long time) {
        QuestBook book = screen.book();
        float thickness = (float) Math.max(1.2, 1.8 * zoom);
        for (Quest quest : chapter.quests) {
            boolean visible = editing || logic.isVisible(quest);
            if (!visible) {
                continue;
            }
            boolean hideLines = quest.effectiveHideDependencyLines();
            for (String depId : quest.dependencies) {
                Quest dep = book.quest(depId);
                if (dep == null || dep.chapter() != chapter) {
                    continue;
                }
                if (!editing && (hideLines || dep.hideDependentLines || !logic.isVisible(dep))) {
                    continue;
                }
                float x1 = sx(objX(dep)), y1 = sy(objY(dep));
                float x2 = sx(objX(quest)), y2 = sy(objY(quest));
                float dx = x2 - x1, dy = y2 - y1;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1) {
                    continue;
                }
                float r1 = (float) (dep.effectiveSize() * NODE * zoom / 2) + 1;
                float r2 = (float) (quest.effectiveSize() * NODE * zoom / 2) + 2;
                if (len <= r1 + r2) {
                    continue;
                }
                float ax = x1 + dx / len * r1, ay = y1 + dy / len * r1;
                float bx = x2 - dx / len * r2, by = y2 - dy / len * r2;
                boolean depDone = logic.isCompleted(dep);
                boolean questDone = logic.isCompleted(quest);
                boolean faded = editing && (hideLines || dep.hideDependentLines);
                int color;
                if (questDone) {
                    color = Theme.withAlpha(Theme.SAGE, 0xA0);
                } else if (depDone && logic.areDependenciesMet(quest)) {
                    color = Theme.withAlpha(Theme.GOLD, 0xD0);
                } else if (depDone) {
                    color = Theme.withAlpha(Theme.SAGE, 0x80);
                } else {
                    color = 0x70857870;
                }
                if (faded) {
                    color = Theme.withAlpha(color, 0x40);
                }
                if (!questDone && depDone && logic.areDependenciesMet(quest)) {
                    ShapeRenderer.line(g, ax, ay, bx, by, thickness, Theme.withAlpha(color, 0x50));
                    ShapeRenderer.dashed(g, ax, ay, bx, by, thickness, 5 * (float) zoom + 2, 4 * (float) zoom + 2, (time % 100000) / 40f * (float) zoom, color);
                } else {
                    ShapeRenderer.line(g, ax, ay, bx, by, thickness, color);
                }
                ShapeRenderer.arrowHead(g, bx, by, dx, dy, Math.max(5, 7 * (float) zoom), color);
            }
        }
    }

    private int[] stateColors(QuestLogic.State state) {
        return switch (state) {
            case LOCKED -> new int[]{0xFF272122, 0xFF5E5450};
            case EXCLUDED -> new int[]{0xFF2A1A1A, 0xFF7A3A3A};
            case COOLDOWN -> new int[]{0xFF272033, Theme.VIOLET};
            case AVAILABLE -> new int[]{0xFF1C2630, Theme.SKY};
            case IN_PROGRESS -> new int[]{0xFF33231A, Theme.EMBER};
            case CLAIMABLE -> new int[]{0xFF3A3218, Theme.GOLD};
            case COMPLETED -> new int[]{0xFF1D2B1D, Theme.SAGE};
        };
    }

    private void renderQuest(GuiGraphics g, Quest quest, QuestLogic logic, boolean editing, boolean hovered, long time) {
        float cx = sx(objX(quest));
        float cy = sy(objY(quest));
        float d = (float) (quest.effectiveSize() * NODE * zoom);
        if (cx + d < screen.canvasLeft() || cx - d > screen.canvasRight() || cy + d < screen.canvasTop() || cy - d > screen.canvasBottom()) {
            return;
        }
        QuestLogic.State state = logic.state(quest, ClientQuests.self());
        int[] colors = stateColors(state);
        int fill = colors[0];
        int outline = colors[1];
        boolean hiddenForPlayers = editing && !logic.isVisible(quest);
        float alpha = hiddenForPlayers ? 0.45f : quest.optional ? 0.8f : 1f;
        if (hovered) {
            fill = Theme.mix(fill, 0xFF4A3A32, 0.5f);
        }
        QuestShape shape = quest.effectiveShape();
        if (state == QuestLogic.State.CLAIMABLE || state == QuestLogic.State.AVAILABLE && !editing) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time / 300.0);
            ShapeRenderer.glow(g, shape, cx, cy, d, Theme.withAlpha(outline, (int) (90 + 80 * pulse)));
        }
        if (selection.contains(quest.id())) {
            ShapeRenderer.node(g, shape == QuestShape.NONE ? QuestShape.CIRCLE : shape, cx, cy, d * 1.22f, 0x00000000, Theme.GOLD);
        }
        ShapeRenderer.node(g, shape, cx, cy, d, applyAlpha(fill, alpha), applyAlpha(outline, alpha));
        Icon icon = quest.displayIcon();
        float iconSize = (float) (d * 0.58 * quest.iconScale);
        if (shape == QuestShape.NONE) {
            iconSize = (float) (d * 0.85 * quest.iconScale);
        }
        if (state == QuestLogic.State.LOCKED && !editing) {
            g.setColor(0.55f, 0.55f, 0.55f, 1f);
        }
        IconRenderer.render(g, icon, cx - iconSize / 2, cy - iconSize / 2, iconSize);
        g.setColor(1, 1, 1, 1);

        float badge = Math.max(7, d * 0.36f);
        float bx = cx + d * 0.30f;
        float by = cy - d * 0.52f;
        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        switch (state) {
            case CLAIMABLE -> {
                float bounce = (float) Math.abs(Math.sin(time / 250.0)) * 2 * (float) zoom;
                GuiIcons.draw(g, "exclamation", bx, by - bounce, badge, Theme.GOLD);
            }
            case COMPLETED -> GuiIcons.draw(g, "check", bx, by, badge, Theme.SAGE);
            case LOCKED -> {
                if (!quest.hideLockIcon) {
                    GuiIcons.draw(g, "lock", bx, by, badge * 0.9f, 0xFF9C8E7E);
                }
            }
            case COOLDOWN -> GuiIcons.draw(g, "back", bx, by, badge * 0.9f, Theme.VIOLET);
            default -> {
            }
        }
        if (ClientQuests.progress().isPinned(ClientQuests.self(), quest.id())) {
            GuiIcons.draw(g, "pinned", cx - d * 0.62f, by, badge * 0.85f, Theme.EMBER);
        }
        if (hiddenForPlayers) {
            GuiIcons.draw(g, "eye_off", cx - d * 0.62f, cy + d * 0.18f, badge * 0.8f, Theme.MUTED);
        }
        if (state == QuestLogic.State.IN_PROGRESS && d >= 14) {
            float w = d * 0.8f;
            int x0 = (int) (cx - w / 2);
            int y0 = (int) (cy + d * 0.5f + 2);
            Theme.bar(g, x0, y0, (int) w, Math.max(2, (int) (2 * zoom)), logic.fraction(quest), Theme.EMBER);
        }
        g.pose().popPose();
    }

    private static int applyAlpha(int color, float alpha) {
        return Theme.withAlpha(color, (int) ((color >>> 24) * alpha));
    }

    private void renderLink(GuiGraphics g, QuestLink link, QuestLogic logic, boolean editing, boolean hovered) {
        Quest target = link.target();
        if (target == null && !editing) {
            return;
        }
        if (target != null && !editing && !logic.isVisible(target)) {
            return;
        }
        float cx = sx(objX(link));
        float cy = sy(objY(link));
        float d = (float) (link.effectiveSize() * NODE * zoom);
        int[] colors = target == null ? new int[]{0xFF3A1A1A, Theme.CRIMSON} : stateColors(logic.state(target, ClientQuests.self()));
        int fill = hovered ? Theme.mix(colors[0], 0xFF4A3A32, 0.5f) : colors[0];
        if (selection.contains(link.id())) {
            ShapeRenderer.node(g, link.effectiveShape() == QuestShape.NONE ? QuestShape.CIRCLE : link.effectiveShape(), cx, cy, d * 1.22f, 0, Theme.GOLD);
        }
        ShapeRenderer.node(g, link.effectiveShape(), cx, cy, d, fill, Theme.withAlpha(colors[1], 0xB0));
        float iconSize = d * 0.55f;
        IconRenderer.render(g, link.displayIcon(), cx - iconSize / 2, cy - iconSize / 2, iconSize);
        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        GuiIcons.draw(g, "link", cx + d * 0.2f, cy + d * 0.2f, Math.max(6, d * 0.32f), Theme.SKY);
        g.pose().popPose();
    }

    private float[] imageRect(ChapterImage image) {
        float w = (float) (image.width * scale());
        float h = (float) (image.height * scale());
        float x = sx(objX(image));
        float y = sy(objY(image));
        if (!image.alignToCorner) {
            x -= w / 2;
            y -= h / 2;
        }
        return new float[]{x, y, w, h};
    }

    private void renderImage(GuiGraphics g, ChapterImage image, boolean editing) {
        if (image.editorOnly && !editing) {
            return;
        }
        float[] r = imageRect(image);
        g.pose().pushPose();
        g.pose().translate(r[0] + r[2] / 2, r[1] + r[3] / 2, 0);
        if (image.rotation != 0) {
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) image.rotation));
        }
        g.pose().translate(-r[2] / 2, -r[3] / 2, 0);
        g.pose().scale(r[2] / 256f, r[3] / 256f, 1);
        RenderSystem.enableBlend();
        int c = image.color;
        g.setColor((c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, image.alpha / 255f * (image.editorOnly ? 0.5f : 1f));
        g.blit(image.image, 0, 0, 256, 256, 0, 0, 1, 1, 1, 1);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        g.pose().popPose();
        if (editing && selection.contains(image.id())) {
            Theme.outline(g, (int) r[0] - 1, (int) r[1] - 1, (int) r[2] + 2, (int) r[3] + 2, Theme.GOLD);
        }
    }

    // ------------------------------------------------------------------ Hit testing and tooltips

    private @Nullable QuestObject objectAt(double mx, double my) {
        if (chapter == null) {
            return null;
        }
        QuestLogic logic = screen.logic();
        boolean editing = screen.editing();
        for (int i = chapter.quests.size() - 1; i >= 0; i--) {
            Quest q = chapter.quests.get(i);
            if ((editing || logic.isVisible(q)) && inNode(mx, my, objX(q), objY(q), q.effectiveSize())) {
                return q;
            }
        }
        for (int i = chapter.links.size() - 1; i >= 0; i--) {
            QuestLink l = chapter.links.get(i);
            Quest target = l.target();
            if ((editing || target != null && logic.isVisible(target)) && inNode(mx, my, objX(l), objY(l), l.effectiveSize())) {
                return l;
            }
        }
        List<ChapterImage> images = new ArrayList<>(chapter.images);
        images.sort(Comparator.comparingInt((ChapterImage i) -> i.order).reversed());
        for (ChapterImage image : images) {
            if (image.editorOnly && !editing) {
                continue;
            }
            if (!editing && image.hover.isEmpty() && image.clickAction == ClickAction.NONE) {
                continue;
            }
            float[] r = imageRect(image);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                return image;
            }
        }
        return null;
    }

    private boolean inNode(double mx, double my, double x, double y, double size) {
        double r = size * NODE * zoom / 2 + 1;
        double dx = mx - sx(x);
        double dy = my - sy(y);
        return dx * dx + dy * dy <= r * r;
    }

    private List<Component> tooltip(QuestObject object, QuestLogic logic, boolean editing) {
        List<Component> lines = new ArrayList<>();
        switch (object) {
            case Quest q -> questTooltip(lines, q, logic, editing);
            case QuestLink l -> {
                Quest target = l.target();
                if (target == null) {
                    lines.add(Component.translatable("corerpg_quests.link.broken").withStyle(ChatFormatting.RED));
                } else {
                    questTooltip(lines, target, logic, editing);
                    lines.add(Component.translatable("corerpg_quests.gui.link_from", target.chapter().displayTitle()).withStyle(ChatFormatting.DARK_AQUA));
                }
            }
            case ChapterImage i -> {
                for (String line : i.hover) {
                    lines.add(TextUtil.format(line));
                }
                if (editing) {
                    lines.add(Component.literal(i.image.toString()).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            default -> {
            }
        }
        if (editing) {
            lines.add(Component.literal(object.kind().getSerializedName() + " " + object.id()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private void questTooltip(List<Component> lines, Quest q, QuestLogic logic, boolean editing) {
        QuestLogic.State state = logic.state(q, ClientQuests.self());
        lines.add(q.displayTitle().copy().withStyle(s -> s.getColor() == null ? s.withColor(stateColors(state)[1] & 0xFFFFFF) : s));
        if (!q.subtitle.isBlank()) {
            lines.add(TextUtil.format(q.subtitle).copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        MutableComponent status = switch (state) {
            case LOCKED -> Component.translatable("corerpg_quests.state.locked");
            case EXCLUDED -> Component.translatable("corerpg_quests.state.excluded");
            case COOLDOWN -> Component.translatable("corerpg_quests.state.cooldown", TextUtil.duration(logic.cooldownRemainingMillis(q) / 1000));
            case AVAILABLE -> Component.translatable("corerpg_quests.state.available");
            case IN_PROGRESS -> Component.translatable("corerpg_quests.state.in_progress", (int) Math.floor(logic.fraction(q) * 100));
            case CLAIMABLE -> Component.translatable("corerpg_quests.state.claimable");
            case COMPLETED -> Component.translatable("corerpg_quests.state.completed");
        };
        lines.add(status.withStyle(s -> s.withColor(stateColors(state)[1] & 0xFFFFFF)));
        if (q.optional) {
            lines.add(Component.translatable("corerpg_quests.state.optional").withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (q.repeatable) {
            lines.add(Component.translatable("corerpg_quests.state.repeatable").withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (editing) {
            lines.add(Component.translatable("corerpg_quests.gui.quest_menu_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ------------------------------------------------------------------ Input

    boolean mouseClicked(double mx, double my, int button) {
        if (!inCanvas(mx, my) || chapter == null) {
            return false;
        }
        QuestObject hit = objectAt(mx, my);
        boolean editing = screen.editing();
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (hit != null) {
                if (editing && !selection.contains(hit.id())) {
                    selection.clear();
                    selection.add(hit.id());
                }
                screen.openMenu(objectMenu(hit), (int) mx, (int) my);
            } else if (editing) {
                screen.openMenu(emptyMenu(ux(mx), uy(my)), (int) mx, (int) my);
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            drag = Drag.PAN;
            pressX = mx;
            pressY = my;
            dragMoved = false;
            pressed = null;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        pressX = mx;
        pressY = my;
        dragMoved = false;
        pressed = hit;
        moveX = moveY = 0;
        if (hit == null) {
            if (editing && QuestBookScreen.hasShiftDown()) {
                drag = Drag.BOX;
                boxX = mx;
                boxY = my;
            } else {
                drag = Drag.PAN;
            }
            return true;
        }
        if (editing) {
            if (QuestBookScreen.hasControlDown() && hit instanceof Quest target && !selection.isEmpty() && !selection.contains(target.id())) {
                toggleDependencies(target);
                drag = Drag.NONE;
                return true;
            }
            if (QuestBookScreen.hasShiftDown()) {
                if (!selection.remove(hit.id())) {
                    selection.add(hit.id());
                }
                drag = Drag.NONE;
                return true;
            }
            if (!selection.contains(hit.id())) {
                selection.clear();
                selection.add(hit.id());
            }
            drag = Drag.MOVE;
        } else {
            // Viewing: dragging pans, a click without movement opens the object.
            drag = Drag.PAN;
        }
        return true;
    }

    boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == Drag.NONE) {
            return false;
        }
        if (Math.abs(mx - pressX) + Math.abs(my - pressY) > 3) {
            dragMoved = true;
        }
        switch (drag) {
            case PAN -> {
                panX -= dx / scale();
                panY -= dy / scale();
            }
            case MOVE -> {
                moveX += dx / scale();
                moveY += dy / scale();
            }
            default -> {
            }
        }
        return true;
    }

    boolean mouseReleased(double mx, double my, int button) {
        Drag was = drag;
        drag = Drag.NONE;
        if (was == Drag.NONE) {
            return false;
        }
        boolean editing = screen.editing();
        switch (was) {
            case PAN -> {
                if (!dragMoved && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (pressed != null) {
                        activate(pressed);
                    } else if (editing) {
                        selection.clear();
                    }
                }
            }
            case BOX -> {
                double x1 = Math.min(boxX, mx), x2 = Math.max(boxX, mx);
                double y1 = Math.min(boxY, my), y2 = Math.max(boxY, my);
                for (Quest q : chapter.quests) {
                    if (inBox(q, x1, y1, x2, y2)) {
                        selection.add(q.id());
                    }
                }
                for (QuestLink l : chapter.links) {
                    if (inBox(l, x1, y1, x2, y2)) {
                        selection.add(l.id());
                    }
                }
            }
            case MOVE -> {
                if (dragMoved) {
                    double dx = snap(moveX);
                    double dy = snap(moveY);
                    if (dx != 0 || dy != 0) {
                        pendingMoveIds = new HashSet<>(selection);
                        pendingMoveX = dx;
                        pendingMoveY = dy;
                        pendingMoveVersion = ClientQuests.version();
                        EditorActions.move(new ArrayList<>(selection), dx, dy);
                    }
                } else if (pressed != null) {
                    activate(pressed);
                }
            }
            default -> {
            }
        }
        pressed = null;
        return true;
    }

    private boolean inBox(QuestObject o, double x1, double y1, double x2, double y2) {
        float x = sx(objX(o));
        float y = sy(objY(o));
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    private void activate(QuestObject object) {
        screen.click();
        switch (object) {
            case Quest q -> screen.openQuest(q);
            case QuestLink l -> {
                Quest target = l.target();
                if (target != null) {
                    screen.selectChapter(target.chapter());
                    centerOn(target.x, target.y);
                    screen.openQuest(target);
                }
            }
            case ChapterImage i -> runClickAction(i);
            default -> {
            }
        }
    }

    private void runClickAction(ChapterImage image) {
        Minecraft mc = screen.mc();
        switch (image.clickAction) {
            case OPEN_QUEST -> screen.focus(image.clickData);
            case OPEN_CHAPTER -> screen.focus(image.clickData);
            case OPEN_URL -> {
                String url = image.clickData;
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    mc.setScreen(new ConfirmLinkScreen(yes -> {
                        if (yes) {
                            Util.getPlatform().openUri(url);
                        }
                        mc.setScreen(screen);
                    }, url, true));
                }
            }
            case RUN_COMMAND -> {
                if (mc.player != null && !image.clickData.isBlank()) {
                    String command = image.clickData.startsWith("/") ? image.clickData.substring(1) : image.clickData;
                    mc.player.connection.sendCommand(command);
                }
            }
            default -> {
            }
        }
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (!inCanvas(mx, my) || amount == 0) {
            return false;
        }
        double beforeX = ux(mx);
        double beforeY = uy(my);
        zoom = Math.max(0.25, Math.min(4, zoom * Math.pow(1.15, amount)));
        panX += beforeX - ux(mx);
        panY += beforeY - uy(my);
        return true;
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        boolean ctrl = QuestBookScreen.hasControlDown();
        if (!screen.editing() || chapter == null) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                deleteSelection();
                return !selection.isEmpty();
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    chapter.quests.forEach(q -> selection.add(q.id()));
                    chapter.links.forEach(l -> selection.add(l.id()));
                    return true;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl) {
                    copySelection();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    paste(mouseUnitX, mouseUnitY);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_D -> {
                if (ctrl) {
                    copySelection();
                    paste(mouseUnitX, mouseUnitY);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                if (selection.isEmpty()) {
                    return false;
                }
                double step = Math.max(0.0625, screen.book().gridScale);
                double dx = key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0;
                double dy = key == GLFW.GLFW_KEY_UP ? -step : key == GLFW.GLFW_KEY_DOWN ? step : 0;
                EditorActions.move(new ArrayList<>(selection), dx, dy);
                return true;
            }
            case GLFW.GLFW_KEY_E -> {
                if (selection.size() == 1) {
                    QuestObject o = screen.book().get(selection.iterator().next());
                    if (o != null) {
                        screen.mc().setScreen(new PropertyEditorScreen(screen, o));
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Editor operations

    private void toggleDependencies(Quest target) {
        boolean changed = false;
        for (String id : selection) {
            if (screen.book().get(id) instanceof Quest source && source != target) {
                if (!target.dependencies.remove(source.id())) {
                    target.dependencies.add(source.id());
                }
                changed = true;
            }
        }
        if (changed) {
            EditorActions.update(target);
        }
    }

    private void deleteSelection() {
        if (selection.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(selection);
        Minecraft mc = screen.mc();
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                ids.forEach(EditorActions::delete);
                selection.clear();
            }
            mc.setScreen(screen);
        }, Component.translatable("corerpg_quests.gui.delete_selected", ids.size()), Component.translatable("corerpg_quests.gui.cannot_undo")));
    }

    private void copySelection() {
        if (selection.isEmpty()) {
            return;
        }
        JsonObject clip = new JsonObject();
        clip.addProperty(CLIPBOARD_KEY, 1);
        JsonArray quests = new JsonArray();
        double ox = Double.NaN;
        double oy = Double.NaN;
        for (String id : selection) {
            if (screen.book().get(id) instanceof Quest q) {
                if (Double.isNaN(ox)) {
                    ox = q.x;
                    oy = q.y;
                }
                JsonObject json = q.toJson(ClientQuests.ctx());
                json.addProperty("x", q.x - ox);
                json.addProperty("y", q.y - oy);
                quests.add(json);
            }
        }
        clip.add("quests", quests);
        screen.mc().keyboardHandler.setClipboard(JsonUtil.COMPACT.toJson(clip));
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("corerpg_quests.gui.copied", quests.size()), false);
    }

    private boolean clipboardHasQuests() {
        try {
            JsonElement e = JsonUtil.parse(screen.mc().keyboardHandler.getClipboard());
            return e.isJsonObject() && e.getAsJsonObject().has(CLIPBOARD_KEY);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Pastes copied quests with fresh ids; dependencies between pasted quests follow them. */
    private void paste(double atX, double atY) {
        if (chapter == null) {
            return;
        }
        JsonObject clip;
        try {
            JsonElement e = JsonUtil.parse(screen.mc().keyboardHandler.getClipboard());
            if (!e.isJsonObject() || !e.getAsJsonObject().has(CLIPBOARD_KEY)) {
                return;
            }
            clip = e.getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        QuestBook book = screen.book();
        Set<String> used = new HashSet<>();
        Map<String, String> remap = new HashMap<>();
        List<JsonObject> quests = new ArrayList<>();
        for (JsonElement e : clip.getAsJsonArray("quests")) {
            JsonObject q = e.getAsJsonObject().deepCopy();
            renew(q, book, used, remap);
            for (String key : new String[]{"tasks", "rewards"}) {
                if (q.has(key)) {
                    for (JsonElement child : q.getAsJsonArray(key)) {
                        renew(child.getAsJsonObject(), book, used, remap);
                    }
                }
            }
            quests.add(q);
        }
        double grid = Math.max(0.0625, book.gridScale);
        double bx = Math.round(atX / grid) * grid;
        double by = Math.round(atY / grid) * grid;
        selection.clear();
        for (JsonObject q : quests) {
            if (q.has("dependencies")) {
                JsonArray deps = new JsonArray();
                for (JsonElement d : q.getAsJsonArray("dependencies")) {
                    deps.add(remap.getOrDefault(d.getAsString(), d.getAsString()));
                }
                q.add("dependencies", deps);
            }
            q.addProperty("x", bx + (q.has("x") ? q.get("x").getAsDouble() : 0));
            q.addProperty("y", by + (q.has("y") ? q.get("y").getAsDouble() : 0));
            EditorActions.create(ObjectKind.QUEST, chapter.id(), q);
            selection.add(q.get("id").getAsString());
        }
    }

    private static void renew(JsonObject obj, QuestBook book, Set<String> used, Map<String, String> remap) {
        String fresh;
        do {
            fresh = Ids.random();
        } while (book.isIdTaken(fresh) || !used.add(fresh));
        remap.put(JsonUtil.getString(obj, "id", ""), fresh);
        obj.addProperty("id", fresh);
    }

    private ContextMenu objectMenu(QuestObject object) {
        ContextMenu m = new ContextMenu();
        Minecraft mc = screen.mc();
        boolean editing = screen.editing();
        switch (object) {
            case Quest q -> {
                m.add(Component.translatable("corerpg_quests.gui.open"), "book", () -> screen.openQuest(q));
                boolean pinned = ClientQuests.progress().isPinned(ClientQuests.self(), q.id());
                m.add(Component.translatable(pinned ? "corerpg_quests.gui.unpin" : "corerpg_quests.gui.pin"), pinned ? "pin" : "pinned",
                        () -> Services.PLATFORM.sendToServer(new Payloads.PinQuest(q.id(), !pinned)));
                if (screen.logic().hasUnclaimedRewards(q, ClientQuests.self())) {
                    m.add(Component.translatable("corerpg_quests.gui.claim_all"), "claim_all", () -> Services.PLATFORM.sendToServer(new Payloads.ClaimAll(q.id())));
                }
                if (!editing) {
                    break;
                }
                m.separator();
                m.add(Component.translatable("corerpg_quests.gui.edit"), "edit", () -> mc.setScreen(new PropertyEditorScreen(screen, q)));
                ContextMenu.Entry tasks = m.add(Component.translatable("corerpg_quests.gui.add_task"), "add", null);
                for (TaskType<?> type : TaskTypes.all()) {
                    tasks.add(Component.translatable(type.translationKey()), null, () -> addTask(q, type));
                }
                ContextMenu.Entry rewards = m.add(Component.translatable("corerpg_quests.gui.add_reward"), "gift", null);
                for (RewardType<?> type : RewardTypes.all()) {
                    rewards.add(Component.translatable(type.translationKey()), null, () -> addReward(q, type));
                }
                ContextMenu.Entry move = m.add(Component.translatable("corerpg_quests.gui.move_to_chapter"), "move", null);
                for (Chapter c : screen.book().chapters) {
                    if (c != chapter) {
                        move.add(c.displayTitle(), null, () -> EditorActions.moveToChapter(new ArrayList<>(selection.isEmpty() ? List.of(q.id()) : selection), c.id()));
                    }
                }
                m.add(Component.translatable("corerpg_quests.gui.set_autofocus"), "star", () -> {
                    chapter.autofocus = q.id();
                    EditorActions.update(chapter);
                });
                if (!q.dependencies.isEmpty()) {
                    m.add(Component.translatable("corerpg_quests.gui.clear_dependencies"), "link", () -> {
                        q.dependencies.clear();
                        EditorActions.update(q);
                    });
                }
                m.separator();
                m.add(Component.translatable("corerpg_quests.gui.copy"), "copy", this::copySelection);
                m.add(Component.translatable("corerpg_quests.gui.copy_id"), "copy", () -> mc.keyboardHandler.setClipboard(q.id()));
                m.add(Component.translatable("corerpg_quests.gui.complete_for_team"), "check", () -> EditorActions.changeProgress(q.id(), true));
                m.add(Component.translatable("corerpg_quests.gui.reset_for_team"), "back", () -> EditorActions.changeProgress(q.id(), false));
                m.separator();
                m.add(Component.translatable("corerpg_quests.gui.delete"), "delete", this::deleteSelection).danger();
            }
            case QuestLink l -> {
                Quest target = l.target();
                if (target != null) {
                    m.add(Component.translatable("corerpg_quests.gui.go_to_quest"), "link", () -> activate(l));
                }
                if (editing) {
                    m.add(Component.translatable("corerpg_quests.gui.edit"), "edit", () -> mc.setScreen(new PropertyEditorScreen(screen, l)));
                    m.separator();
                    m.add(Component.translatable("corerpg_quests.gui.delete"), "delete", this::deleteSelection).danger();
                }
            }
            case ChapterImage i -> {
                if (editing) {
                    m.add(Component.translatable("corerpg_quests.gui.edit"), "edit", () -> mc.setScreen(new PropertyEditorScreen(screen, i)));
                    m.add(Component.translatable("corerpg_quests.gui.delete"), "delete", this::deleteSelection).danger();
                }
            }
            default -> {
            }
        }
        return m;
    }

    private ContextMenu emptyMenu(double x, double y) {
        ContextMenu m = new ContextMenu();
        Minecraft mc = screen.mc();
        double grid = Math.max(0.0625, screen.book().gridScale);
        double sxu = Math.round(x / grid) * grid;
        double syu = Math.round(y / grid) * grid;
        m.add(Component.translatable("corerpg_quests.gui.new_quest"), "add", () -> {
            JsonObject data = new JsonObject();
            data.addProperty("title", "New Quest");
            data.addProperty("x", sxu);
            data.addProperty("y", syu);
            EditorActions.focusNextCreated("edit");
            EditorActions.create(ObjectKind.QUEST, chapter.id(), data);
        });
        m.add(Component.translatable("corerpg_quests.gui.new_link"), "link", () -> mc.setScreen(PickerScreen.quests(screen,
                Component.translatable("corerpg_quests.gui.pick_quest"), screen.book(), quest -> {
                    JsonObject data = new JsonObject();
                    data.addProperty("quest", quest.id());
                    data.addProperty("x", sxu);
                    data.addProperty("y", syu);
                    EditorActions.create(ObjectKind.LINK, chapter.id(), data);
                })));
        m.add(Component.translatable("corerpg_quests.gui.new_image"), "image", () -> {
            JsonObject data = new JsonObject();
            data.addProperty("x", sxu);
            data.addProperty("y", syu);
            EditorActions.focusNextCreated("edit");
            EditorActions.create(ObjectKind.IMAGE, chapter.id(), data);
        });
        if (clipboardHasQuests()) {
            m.add(Component.translatable("corerpg_quests.gui.paste"), "copy", () -> paste(x, y));
        }
        m.separator();
        m.add(Component.translatable("corerpg_quests.gui.edit_chapter"), "settings", () -> mc.setScreen(new PropertyEditorScreen(screen, chapter)));
        return m;
    }

    private void addTask(Quest quest, TaskType<?> type) {
        JsonObject data = new JsonObject();
        data.addProperty("type", type.shortId());
        EditorActions.focusNextCreated("edit");
        EditorActions.create(ObjectKind.TASK, quest.id(), data);
    }

    private void addReward(Quest quest, RewardType<?> type) {
        JsonObject data = new JsonObject();
        data.addProperty("type", type.shortId());
        EditorActions.focusNextCreated("edit");
        EditorActions.create(ObjectKind.REWARD, quest.id(), data);
    }
}
