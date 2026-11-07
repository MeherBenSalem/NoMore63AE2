package dev.nightbeam.corerpg.quests.client.screen;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.ChapterGroup;
import dev.nightbeam.corerpg.quests.book.ObjectKind;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.KeyBinds;
import dev.nightbeam.corerpg.quests.client.render.GuiIcons;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.screen.editor.PropertyEditorScreen;
import dev.nightbeam.corerpg.quests.client.screen.editor.RewardTablesScreen;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.progress.QuestLogic;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The quest book: chapter list on the left, the chapter canvas in the middle, a quest panel on top when a quest is
 * opened. In editing mode the same screen becomes the editor.
 */
public class QuestBookScreen extends Screen {
    private static final Set<String> COLLAPSED = new HashSet<>();
    private static final int TOP = 24;
    private static final int ROW = 16;

    final CanvasView canvas = new CanvasView(this);
    @Nullable QuestPanel panel;
    @Nullable ContextMenu menu;
    private @Nullable Chapter chapter;
    private int sidebarWidth;
    private double sidebarScroll;
    private int lastVersion = -1;
    private EditBox search;
    private final List<Quest> searchResults = new ArrayList<>();
    private final List<IconButton> buttons = new ArrayList<>();
    private String pendingFocus;
    private @Nullable List<Component> tooltip;
    private boolean initialized;

    record IconButton(int x, int y, int size, String icon, Component tooltip, int color, Runnable action) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + size && my >= y && my < y + size;
        }
    }

    private QuestBookScreen(String focus) {
        super(Component.translatable("corerpg_quests.book"));
        this.pendingFocus = focus;
    }

    public static void open(String focus) {
        Minecraft mc = Minecraft.getInstance();
        QuestBook book = ClientQuests.book();
        if (book == null || !ClientQuests.ready()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("corerpg_quests.gui.not_ready").withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (book.disableGui && !ClientQuests.canEdit()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("corerpg_quests.gui.disabled"), true);
            }
            return;
        }
        mc.setScreen(new QuestBookScreen(focus == null ? "" : focus));
    }

    public Minecraft mc() {
        return minecraft;
    }

    public QuestBook book() {
        QuestBook book = ClientQuests.book();
        return book != null ? book : new QuestBook();
    }

    public QuestLogic logic() {
        return ClientQuests.logic();
    }

    public boolean editing() {
        return ClientQuests.editing();
    }

    public @Nullable Chapter chapter() {
        return chapter;
    }

    int canvasLeft() {
        return sidebarWidth;
    }

    int canvasTop() {
        return TOP;
    }

    int canvasRight() {
        return width;
    }

    int canvasBottom() {
        return editing() ? height - 12 : height;
    }

    @Override
    protected void init() {
        sidebarWidth = Math.max(110, Math.min(170, width / 5));
        search = new EditBox(font, width - 150, 8, 96, 10, Component.translatable("corerpg_quests.gui.search"));
        search.setBordered(false);
        search.setHint(Component.translatable("corerpg_quests.gui.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(this::updateSearch);
        search.setMaxLength(64);
        addWidget(search);
        if (!initialized) {
            initialized = true;
            lastVersion = ClientQuests.version();
            if (!pendingFocus.isEmpty()) {
                focus(pendingFocus);
                pendingFocus = "";
            } else {
                Chapter last = CoreConfig.CLIENT.rememberChapter.get() ? book().chapter(ClientQuests.lastChapter()) : null;
                selectChapter(last != null && isChapterShown(last) ? last : firstChapter());
            }
        } else {
            canvas.clampView();
        }
        if (panel != null) {
            panel.layout();
        }
        rebuildButtons();
    }

    /** Opens a quest (in its chapter) or a chapter by id. */
    public void focus(String id) {
        QuestObject object = book().get(id);
        if (object instanceof Quest quest) {
            selectChapter(quest.chapter());
            canvas.centerOn(quest.x, quest.y);
            openQuest(quest);
        } else if (object instanceof Chapter c) {
            selectChapter(c);
        } else {
            selectChapter(firstChapter());
        }
    }

    private @Nullable Chapter firstChapter() {
        for (Chapter c : book().chapters) {
            if (isChapterShown(c)) {
                return c;
            }
        }
        return null;
    }

    boolean isChapterShown(Chapter c) {
        if (editing()) {
            return true;
        }
        QuestLogic logic = logic();
        if (!logic.isChapterVisible(c)) {
            return false;
        }
        return CoreConfig.CLIENT.showCompletedChapters.get() || !logic.isChapterComplete(c) || logic.hasUnclaimedRewards(c, ClientQuests.self());
    }

    public void selectChapter(@Nullable Chapter c) {
        chapter = c;
        canvas.reset(c);
        if (c != null) {
            ClientQuests.setLastChapter(c.id());
        }
        closePanel();
        if (search != null) {
            rebuildButtons();
        }
    }

    public void openQuest(Quest quest) {
        if (quest.chapter() != chapter) {
            selectChapter(quest.chapter());
        }
        panel = new QuestPanel(this, quest);
        panel.layout();
    }

    public void closePanel() {
        panel = null;
    }

    /** Called when the synced data changed: re-resolve objects by id. */
    private void refresh() {
        lastVersion = ClientQuests.version();
        QuestBook book = book();
        if (chapter != null) {
            Chapter fresh = book.chapter(chapter.id());
            if (fresh == null || !isChapterShown(fresh)) {
                selectChapter(firstChapter());
            } else {
                chapter = fresh;
                canvas.rebind(fresh);
            }
        } else if (firstChapter() != null) {
            selectChapter(firstChapter());
        }
        if (panel != null) {
            Quest fresh = book.quest(panel.quest().id());
            if (fresh == null) {
                panel = null;
            } else {
                panel.rebind(fresh);
            }
        }
        String created = ClientQuests.takeLastCreated();
        if (created != null && EditorActions.hasPendingFocus()) {
            String hint = EditorActions.takePendingFocus();
            QuestObject object = book.get(created);
            if (object != null) {
                onCreated(hint, object);
            }
        }
        updateSearch(search == null ? "" : search.getValue());
        rebuildButtons();
    }

    private void onCreated(String hint, QuestObject created) {
        switch (created) {
            case Chapter c -> selectChapter(c);
            case Quest q -> {
                openQuest(q);
                if (hint.equals("edit")) {
                    minecraft.setScreen(new PropertyEditorScreen(this, q));
                }
            }
            default -> {
                if (hint.equals("edit")) {
                    minecraft.setScreen(new PropertyEditorScreen(this, created));
                }
            }
        }
    }

    @Override
    public void tick() {
        if (!ClientQuests.ready()) {
            onClose();
            return;
        }
        if (ClientQuests.version() != lastVersion) {
            refresh();
        }
    }

    // ------------------------------------------------------------------ Buttons

    void rebuildButtons() {
        buttons.clear();
        int x = width - 20;
        int y = 4;
        buttons.add(new IconButton(x, y, 16, "close", Component.translatable("gui.done"), Theme.MUTED, this::onClose));
        x -= 18;
        if (ClientQuests.canEdit()) {
            boolean editing = editing();
            buttons.add(new IconButton(x, y, 16, editing ? "eye" : "edit", Component.translatable(editing ? "corerpg_quests.gui.stop_editing" : "corerpg_quests.gui.start_editing"),
                    editing ? Theme.GOLD : Theme.MUTED, () -> Services.PLATFORM.sendToServer(new Payloads.SetEditMode(!editing()))));
            x -= 18;
        }
        buttons.add(new IconButton(x, y, 16, "team", Component.translatable("corerpg_quests.gui.team"), Theme.MUTED, () -> minecraft.setScreen(new TeamScreen(this))));
        x -= 18;
        if (chapter != null && logic().hasUnclaimedRewards(chapter, ClientQuests.self())) {
            String id = chapter.id();
            buttons.add(new IconButton(x, y, 16, "claim_all", Component.translatable("corerpg_quests.gui.claim_all_chapter"), Theme.GOLD,
                    () -> Services.PLATFORM.sendToServer(new Payloads.ClaimAll(id))));
            x -= 18;
        }
        if (!book().emergencyItems.isEmpty()) {
            buttons.add(new IconButton(x, y, 16, "gift", Component.translatable("corerpg_quests.gui.emergency_items"), Theme.MUTED,
                    () -> Services.PLATFORM.sendToServer(new Payloads.EmergencyItems())));
            x -= 18;
        }
        buttons.add(new IconButton(x, y, 16, "recenter", Component.translatable("corerpg_quests.gui.recenter"), Theme.MUTED, () -> canvas.reset(chapter)));
        x -= 18;
        search.setX(x - 100);
        search.setY(8);
        if (editing()) {
            int bx = 6;
            int by = height - 26;
            buttons.add(new IconButton(bx, by, 16, "add", Component.translatable("corerpg_quests.gui.new_chapter"), Theme.EMBER, this::newChapter));
            buttons.add(new IconButton(bx + 20, by, 16, "group", Component.translatable("corerpg_quests.gui.new_group"), Theme.EMBER, this::newGroup));
            buttons.add(new IconButton(bx + 40, by, 16, "table", Component.translatable("corerpg_quests.gui.reward_tables"), Theme.EMBER,
                    () -> minecraft.setScreen(new RewardTablesScreen(this))));
            buttons.add(new IconButton(bx + 60, by, 16, "settings", Component.translatable("corerpg_quests.gui.book_settings"), Theme.EMBER,
                    () -> minecraft.setScreen(new PropertyEditorScreen(this, book()))));
        }
    }

    private void newChapter() {
        JsonObject data = new JsonObject();
        data.addProperty("title", "New Chapter");
        EditorActions.focusNextCreated("select");
        EditorActions.create(ObjectKind.CHAPTER, "", data);
    }

    private void newGroup() {
        JsonObject data = new JsonObject();
        data.addProperty("title", "New Group");
        EditorActions.focusNextCreated("edit");
        EditorActions.create(ObjectKind.GROUP, "", data);
    }

    // ------------------------------------------------------------------ Search

    private void updateSearch(String text) {
        searchResults.clear();
        String q = text.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return;
        }
        QuestLogic logic = logic();
        for (Quest quest : book().allQuests()) {
            if (!editing() && (!logic.isVisible(quest) || !isChapterShown(quest.chapter()) || logic.isTextHidden(quest))) {
                continue;
            }
            String hay = TextUtil.plain(quest.displayTitle().getString()).toLowerCase(Locale.ROOT) + " " + quest.id();
            if (hay.contains(q)) {
                searchResults.add(quest);
                if (searchResults.size() >= 12) {
                    break;
                }
            }
        }
    }

    private int searchListX() {
        return Math.min(search.getX() - 16, width - 204);
    }

    // ------------------------------------------------------------------ Rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xFF141012, 0xFF0A0708);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tooltip = null;
        renderBackground(g, mouseX, mouseY, partialTick);
        boolean blocked = panel != null || menu != null;
        int mx = blocked ? -1000 : mouseX;
        int my = blocked ? -1000 : mouseY;

        canvas.render(g, mx, my, partialTick);
        renderSidebar(g, mx, my);
        renderTopBar(g, mx, my);
        if (editing()) {
            renderStatusBar(g);
        }
        if (panel != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(0, 0, width, height, 0x90000000);
            panel.render(g, menu == null ? mouseX : -1000, menu == null ? mouseY : -1000, partialTick);
            g.pose().popPose();
        }
        if (menu != null) {
            menu.render(g, font, mouseX, mouseY, width, height);
        }
        if (tooltip != null && menu == null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    void setTooltip(List<Component> lines) {
        this.tooltip = lines;
    }

    private void renderTopBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(sidebarWidth, 0, width, TOP, 0xE6151012);
        g.fill(sidebarWidth, TOP - 1, width, TOP, Theme.LINE);
        int titleRight = search.getX() - 22;
        if (chapter != null) {
            IconRenderer.render(g, chapter.displayIcon(), sidebarWidth + 6, 4, 16);
            Component title = Theme.display(chapter.displayTitle());
            if (!chapter.subtitle.isEmpty() && !TextUtil.isBlank(chapter.subtitle)) {
                Theme.drawFitted(g, font, title, sidebarWidth + 26, 3, titleRight - sidebarWidth - 28, Theme.TITLE);
                String sub = font.plainSubstrByWidth(TextUtil.plain(chapter.subtitle.getFirst()), titleRight - sidebarWidth - 28);
                g.drawString(font, sub, sidebarWidth + 26, 13, Theme.MUTED, false);
            } else {
                Theme.drawFitted(g, font, title, sidebarWidth + 26, 8, titleRight - sidebarWidth - 28, Theme.TITLE);
            }
        } else {
            g.drawString(font, Component.translatable(editing() ? "corerpg_quests.gui.no_chapters_edit" : "corerpg_quests.gui.no_chapters"),
                    sidebarWidth + 8, 8, Theme.MUTED, false);
        }
        int sx = search.getX() - 14;
        g.fill(sx - 2, 5, search.getX() + search.getWidth() + 2, 19, 0x80000000);
        Theme.outline(g, sx - 2, 5, search.getWidth() + 18, 14, search.isFocused() ? Theme.EMBER : Theme.LINE);
        GuiIcons.draw(g, "search", sx, 7, 10, Theme.MUTED);
        search.render(g, mouseX, mouseY, 0);
        for (IconButton b : buttons) {
            if (b.y < TOP) {
                drawButton(g, b, mouseX, mouseY);
            }
        }
        if (!searchResults.isEmpty() && search.isFocused()) {
            int rx = searchListX();
            int rw = 200;
            int ry = 21;
            int rh = searchResults.size() * 14 + 4;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            Theme.panel(g, rx, ry, rw, rh, Theme.EMBER);
            int y = ry + 2;
            for (Quest q : searchResults) {
                boolean hover = mouseX >= rx && mouseX < rx + rw && mouseY >= y && mouseY < y + 14;
                if (hover) {
                    g.fill(rx + 1, y, rx + rw - 1, y + 14, 0x40E8743B);
                }
                IconRenderer.render(g, q.displayIcon(), rx + 4, y + 2, 10);
                g.drawString(font, font.plainSubstrByWidth(q.displayTitle().getString(), rw - 80), rx + 18, y + 3, Theme.TEXT, false);
                String ch = font.plainSubstrByWidth(TextUtil.plain(q.chapter().displayTitle().getString()), 56);
                g.drawString(font, ch, rx + rw - 4 - font.width(ch), y + 3, Theme.FAINT, false);
                y += 14;
            }
            g.pose().popPose();
        }
    }

    private void drawButton(GuiGraphics g, IconButton b, int mouseX, int mouseY) {
        boolean hover = b.hit(mouseX, mouseY);
        if (hover) {
            g.fill(b.x - 1, b.y - 1, b.x + b.size + 1, b.y + b.size + 1, 0x40E8743B);
            setTooltip(List.of(b.tooltip));
        }
        GuiIcons.draw(g, b.icon, b.x, b.y, b.size, hover ? Theme.TITLE : b.color);
    }

    private int listBottom() {
        return editing() ? height - 30 : height - 4;
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, sidebarWidth, height, 0xF2141012);
        g.fill(sidebarWidth - 1, 0, sidebarWidth, height, Theme.LINE);
        QuestBook book = book();
        QuestLogic logic = logic();
        IconRenderer.render(g, book.displayIcon(), 6, 4, 16);
        Theme.drawFitted(g, font, Theme.display(book.displayTitle()), 26, 4, sidebarWidth - 30, Theme.GOLD);
        Theme.bar(g, 26, 15, sidebarWidth - 32, 3, logic.bookFraction(), Theme.GOLD);
        g.fill(0, TOP - 1, sidebarWidth, TOP, Theme.LINE);

        int listBottom = listBottom();
        g.enableScissor(0, TOP, sidebarWidth - 1, listBottom);
        int y = TOP + 3 - (int) sidebarScroll;
        for (Map.Entry<ChapterGroup, List<Chapter>> entry : book.chaptersByGroup().entrySet()) {
            ChapterGroup group = entry.getKey();
            List<Chapter> shown = shownChapters(entry.getValue());
            if (group != null) {
                if (shown.isEmpty() && !editing()) {
                    continue;
                }
                boolean collapsed = isCollapsed(group);
                boolean hover = mouseX < sidebarWidth && mouseY >= y && mouseY < y + ROW && mouseY >= TOP && mouseY < listBottom;
                g.drawString(font, collapsed ? "▸" : "▾", 5, y + 4, Theme.FAINT, false);
                Theme.drawFitted(g, font, Theme.display(Component.literal(TextUtil.plain(group.displayTitle().getString()))), 14, y + 4, sidebarWidth - 20,
                        hover ? Theme.TITLE : Theme.MUTED);
                y += ROW;
                if (collapsed) {
                    continue;
                }
            }
            for (Chapter c : shown) {
                renderChapterRow(g, c, logic, y, mouseX, mouseY, listBottom);
                y += ROW;
            }
        }
        g.disableScissor();
        int contentHeight = y + (int) sidebarScroll - TOP;
        int view = listBottom - TOP;
        if (contentHeight > view) {
            sidebarScroll = Math.min(sidebarScroll, contentHeight - view);
            int barH = Math.max(12, view * view / contentHeight);
            int barY = TOP + (int) ((view - barH) * (sidebarScroll / Math.max(1, contentHeight - view)));
            g.fill(sidebarWidth - 3, barY, sidebarWidth - 1, barY + barH, Theme.LINE);
        } else {
            sidebarScroll = 0;
        }
        if (editing()) {
            g.fill(0, height - 30, sidebarWidth - 1, height - 29, Theme.LINE);
        }
        for (IconButton b : buttons) {
            if (b.y >= TOP) {
                drawButton(g, b, mouseX, mouseY);
            }
        }
    }

    private List<Chapter> shownChapters(List<Chapter> chapters) {
        List<Chapter> shown = new ArrayList<>();
        for (Chapter c : chapters) {
            if (isChapterShown(c)) {
                shown.add(c);
            }
        }
        return shown;
    }

    private void renderChapterRow(GuiGraphics g, Chapter c, QuestLogic logic, int y, int mouseX, int mouseY, int listBottom) {
        boolean selected = c == chapter;
        boolean hover = mouseX < sidebarWidth - 1 && mouseY >= y && mouseY < y + ROW && mouseY >= TOP && mouseY < listBottom;
        if (selected) {
            g.fill(0, y, sidebarWidth - 1, y + ROW, 0x50E8743B);
            g.fill(0, y, 2, y + ROW, Theme.EMBER);
        } else if (hover) {
            g.fill(0, y, sidebarWidth - 1, y + ROW, 0x25FFFFFF);
        }
        IconRenderer.render(g, c.displayIcon(), 6, y + 2, 12);
        boolean complete = logic.isChapterComplete(c);
        boolean claimable = logic.hasUnclaimedRewards(c, ClientQuests.self());
        boolean hiddenForPlayers = editing() && !logic.isChapterVisible(c);
        int textColor = hiddenForPlayers ? Theme.FAINT : complete ? Theme.SAGE : selected ? Theme.TITLE : Theme.TEXT;
        int right = sidebarWidth - 6;
        if (claimable) {
            GuiIcons.draw(g, "exclamation", right - 9, y + 3, 9, Theme.GOLD);
            right -= 11;
        } else if (complete) {
            GuiIcons.draw(g, "check", right - 9, y + 3, 9, Theme.SAGE);
            right -= 11;
        } else {
            String pct = (int) Math.floor(logic.chapterFraction(c) * 100) + "%";
            g.drawString(font, pct, right - font.width(pct), y + 4, Theme.FAINT, false);
            right -= font.width(pct) + 3;
        }
        g.drawString(font, font.plainSubstrByWidth(c.displayTitle().getString(), right - 22), 22, y + 4, textColor, false);
    }

    private void renderStatusBar(GuiGraphics g) {
        int y = height - 12;
        g.fill(sidebarWidth, y, width, height, 0xE6201418);
        g.fill(sidebarWidth, y, width, y + 1, Theme.EMBER);
        String info = canvas.statusText();
        int infoWidth = font.width(info);
        Component hint = Component.translatable("corerpg_quests.gui.editor_hint");
        g.drawString(font, font.substrByWidth(hint, width - sidebarWidth - infoWidth - 20).getString(), sidebarWidth + 6, y + 2, Theme.MUTED, false);
        g.drawString(font, info, width - 6 - infoWidth, y + 2, Theme.GOLD, false);
    }

    // ------------------------------------------------------------------ Input

    private static boolean isCollapsed(ChapterGroup group) {
        return COLLAPSED.contains(group.id()) != group.collapsedByDefault;
    }

    private static void toggleCollapsed(ChapterGroup group) {
        if (!COLLAPSED.remove(group.id())) {
            COLLAPSED.add(group.id());
        }
    }

    void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
        if (panel != null) {
            if (!panel.mouseClicked(mx, my, button) && !panel.contains(mx, my)) {
                closePanel();
            }
            return true;
        }
        if (!searchResults.isEmpty() && search.isFocused()) {
            int rx = searchListX();
            int y = 23;
            for (Quest q : List.copyOf(searchResults)) {
                if (mx >= rx && mx < rx + 200 && my >= y && my < y + 14) {
                    search.setValue("");
                    search.setFocused(false);
                    focus(q.id());
                    return true;
                }
                y += 14;
            }
        }
        for (IconButton b : List.copyOf(buttons)) {
            if (b.hit(mx, my)) {
                click();
                b.action.run();
                return true;
            }
        }
        if (search.mouseClicked(mx, my, button)) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        search.setFocused(false);
        if (mx < sidebarWidth) {
            return clickSidebar(mx, my, button);
        }
        if (my < TOP) {
            return false;
        }
        return canvas.mouseClicked(mx, my, button);
    }

    private boolean clickSidebar(double mx, double my, int button) {
        int listBottom = listBottom();
        if (my < TOP || my >= listBottom) {
            return false;
        }
        int y = TOP + 3 - (int) sidebarScroll;
        for (Map.Entry<ChapterGroup, List<Chapter>> entry : book().chaptersByGroup().entrySet()) {
            ChapterGroup group = entry.getKey();
            List<Chapter> shown = shownChapters(entry.getValue());
            if (group != null) {
                if (shown.isEmpty() && !editing()) {
                    continue;
                }
                if (my >= y && my < y + ROW) {
                    if (button == 1 && editing()) {
                        openGroupMenu(group, (int) mx, (int) my);
                    } else {
                        toggleCollapsed(group);
                    }
                    return true;
                }
                y += ROW;
                if (isCollapsed(group)) {
                    continue;
                }
            }
            for (Chapter c : shown) {
                if (my >= y && my < y + ROW) {
                    if (button == 1 && editing()) {
                        openChapterMenu(c, (int) mx, (int) my);
                    } else if (c != chapter) {
                        click();
                        selectChapter(c);
                    }
                    return true;
                }
                y += ROW;
            }
        }
        return false;
    }

    void openMenu(ContextMenu newMenu, int x, int y) {
        if (!newMenu.isEmpty()) {
            newMenu.open(font, x, y, width, height);
            menu = newMenu;
        }
    }

    private void openChapterMenu(Chapter c, int x, int y) {
        ContextMenu m = new ContextMenu();
        m.add(Component.translatable("corerpg_quests.gui.edit"), "edit", () -> minecraft.setScreen(new PropertyEditorScreen(this, c)));
        int index = book().chapters.indexOf(c);
        m.add(Component.translatable("corerpg_quests.gui.move_up"), "arrow_left", () -> EditorActions.reorder(c.id(), index - 1)).disabled(index <= 0);
        m.add(Component.translatable("corerpg_quests.gui.move_down"), "arrow_right", () -> EditorActions.reorder(c.id(), index + 1)).disabled(index >= book().chapters.size() - 1);
        ContextMenu.Entry groups = m.add(Component.translatable("corerpg_quests.gui.set_group"), "group", null);
        groups.add(Component.translatable("corerpg_quests.gui.no_group"), null, () -> setGroup(c, ""));
        for (ChapterGroup g : book().groups) {
            groups.add(g.displayTitle(), null, () -> setGroup(c, g.id()));
        }
        m.separator();
        m.add(Component.translatable("corerpg_quests.gui.complete_for_team"), "check", () -> EditorActions.changeProgress(c.id(), true));
        m.add(Component.translatable("corerpg_quests.gui.reset_for_team"), "back", () -> EditorActions.changeProgress(c.id(), false));
        m.add(Component.translatable("corerpg_quests.gui.copy_id"), "copy", () -> minecraft.keyboardHandler.setClipboard(c.id()));
        m.separator();
        m.add(Component.translatable("corerpg_quests.gui.delete"), "delete", () -> confirmDelete(c)).danger();
        openMenu(m, x, y);
    }

    private void setGroup(Chapter c, String group) {
        c.group = group;
        EditorActions.update(c);
    }

    private void openGroupMenu(ChapterGroup group, int x, int y) {
        ContextMenu m = new ContextMenu();
        m.add(Component.translatable("corerpg_quests.gui.edit"), "edit", () -> minecraft.setScreen(new PropertyEditorScreen(this, group)));
        int index = book().groups.indexOf(group);
        m.add(Component.translatable("corerpg_quests.gui.move_up"), "arrow_left", () -> EditorActions.reorder(group.id(), index - 1)).disabled(index <= 0);
        m.add(Component.translatable("corerpg_quests.gui.move_down"), "arrow_right", () -> EditorActions.reorder(group.id(), index + 1)).disabled(index >= book().groups.size() - 1);
        m.separator();
        m.add(Component.translatable("corerpg_quests.gui.delete"), "delete", () -> confirmDelete(group)).danger();
        openMenu(m, x, y);
    }

    void confirmDelete(QuestObject object) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                EditorActions.delete(object.id());
            }
            minecraft.setScreen(this);
        }, Component.translatable("corerpg_quests.gui.delete_confirm", object.displayTitle()),
                Component.translatable("corerpg_quests.gui.delete_confirm.detail", object.kind().getSerializedName(), object.id())));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (panel != null) {
            return panel.mouseReleased(mx, my, button);
        }
        return canvas.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panel != null) {
            return panel.mouseDragged(mx, my, button, dx, dy);
        }
        return canvas.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (menu != null) {
            return true;
        }
        if (panel != null) {
            return panel.mouseScrolled(mx, my, scrollY);
        }
        if (mx < sidebarWidth) {
            sidebarScroll = Math.max(0, sidebarScroll - scrollY * ROW * 2);
            return true;
        }
        return canvas.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) {
                menu = null;
                return true;
            }
            if (panel != null) {
                closePanel();
                return true;
            }
            if (search.isFocused()) {
                search.setFocused(false);
                return true;
            }
        }
        if (search.isFocused()) {
            return search.keyPressed(key, scan, modifiers);
        }
        if (key == GLFW.GLFW_KEY_F && hasControlDown()) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (panel != null && panel.keyPressed(key, scan, modifiers)) {
            return true;
        }
        if (panel == null && menu == null && canvas.keyPressed(key, scan, modifiers)) {
            return true;
        }
        if (KeyBinds.OPEN_BOOK.matches(key, scan) && panel == null) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (search.isFocused()) {
            return search.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return book().pauseGame;
    }
}
