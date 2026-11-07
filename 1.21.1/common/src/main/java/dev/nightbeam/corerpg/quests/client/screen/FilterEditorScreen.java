package dev.nightbeam.corerpg.quests.client.screen;

import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.screen.filter.FilterPreview;
import dev.nightbeam.corerpg.quests.client.screen.team.Hit;
import dev.nightbeam.corerpg.quests.client.widget.ThemedButton;
import dev.nightbeam.corerpg.quests.filter.FilterException;
import dev.nightbeam.corerpg.quests.filter.FilterParser;
import dev.nightbeam.corerpg.quests.filter.FilterTypes;
import dev.nightbeam.corerpg.quests.filter.ItemFilter;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Edits a filter expression: on the smart filter held in a hand, or (through {@link #forExpression}) any filter
 * property for another screen. Validates as you type, shows where an error is, previews the matching items and lists
 * every filter function with an example that can be inserted with a click.
 */
public class FilterEditorScreen extends Screen {
    public static final int MAX_LENGTH = 4000;
    private static final int CELL = 18;

    private record RefEntry(Component title, Component detail, String insert) {
    }

    private final @Nullable Screen parent;
    private final @Nullable InteractionHand hand;
    private final @Nullable Consumer<String> onSave;
    private String expression;

    private @Nullable MultiLineEditBox editor;
    private @Nullable ThemedButton saveButton;
    private final List<Hit> hits = new ArrayList<>();
    private final List<RefEntry> reference = new ArrayList<>();

    private @Nullable String error;
    private int errorPosition = -1;
    private final FilterPreview preview = new FilterPreview();
    private int previewDelay;
    private double gridScroll;
    private double refScroll;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int leftW;
    private int refX;
    private int refW;
    private int gridTop;
    private int gridBottom;

    public FilterEditorScreen(InteractionHand hand) {
        this(null, hand, readHand(hand), null);
    }

    private FilterEditorScreen(@Nullable Screen parent, @Nullable InteractionHand hand, String initial, @Nullable Consumer<String> onSave) {
        super(Component.translatable("corerpg_quests.gui.filter.title"));
        this.parent = parent;
        this.hand = hand;
        this.onSave = onSave;
        this.expression = initial == null ? "" : initial;
        validate();
        previewDelay = 1;
    }

    /** Opens the editor for any filter string; Save hands the result to {@code onSave} and returns to {@code parent}. */
    public static FilterEditorScreen forExpression(Screen parent, String initial, Consumer<String> onSave) {
        return new FilterEditorScreen(parent, null, initial, onSave);
    }

    private static String readHand(InteractionHand hand) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || ModRegistry.FILTER_COMPONENT == null) {
            return "";
        }
        String value = player.getItemInHand(hand).get(ModRegistry.FILTER_COMPONENT.get());
        return value == null ? "" : value;
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("corerpg_quests.gui.filter." + key, args);
    }

    // ------------------------------------------------------------------ Layout

    @Override
    protected void init() {
        pw = Math.min(width - 16, 540);
        ph = Math.min(height - 16, 310);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        refW = Math.max(120, Math.min(190, (int) (pw * 0.34)));
        refX = px + pw - 8 - refW;
        leftW = refX - 8 - (px + 8);

        int editorY = py + 30;
        int editorH = Math.max(36, Math.min(64, ph / 5));
        editor = new MultiLineEditBox(font, px + 8, editorY, leftW, editorH, t("placeholder"), t("expression"));
        editor.setCharacterLimit(MAX_LENGTH);
        editor.setValue(expression);
        editor.setValueListener(value -> {
            expression = value;
            validate();
            previewDelay = 6;
        });
        addRenderableWidget(editor);
        setInitialFocus(editor);

        gridTop = editorY + editorH + 40;
        gridBottom = py + ph - 42;

        int bottomY = py + ph - 24;
        saveButton = new ThemedButton(px + 8, bottomY, 70, 18, t("save"), b -> save(), Theme.SAGE);
        addRenderableWidget(saveButton);
        addRenderableWidget(new ThemedButton(px + 82, bottomY, 70, 18, t("clear"), b -> {
            if (editor != null) {
                editor.setValue("");
            }
        }, Theme.CRIMSON));
        addRenderableWidget(new ThemedButton(px + pw - 8 - 70, bottomY, 70, 18, t("cancel"), b -> onClose(), Theme.MUTED));
        updateSave();
        buildReference();
    }

    private void buildReference() {
        reference.clear();
        reference.add(new RefEntry(Component.literal("minecraft:stone"), t("shorthand.item"), "minecraft:stone"));
        reference.add(new RefEntry(Component.literal("#c:ingots"), t("shorthand.tag"), "#c:ingots"));
        reference.add(new RefEntry(Component.literal("@minecraft"), t("shorthand.mod"), "@minecraft"));
        for (FilterTypes.Type type : FilterTypes.all()) {
            Component detail = Component.literal(type.example());
            if (!type.aliases().isEmpty()) {
                detail = Component.literal(type.example() + "  ").append(t("aliases", String.join(", ", type.aliases())));
            }
            reference.add(new RefEntry(Component.literal(type.signature()), detail, type.example()));
        }
    }

    // ------------------------------------------------------------------ Logic

    private void validate() {
        String text = normalized();
        error = null;
        errorPosition = -1;
        if (text.length() > MAX_LENGTH) {
            error = t("too_long", text.length(), MAX_LENGTH).getString();
        } else if (!text.isBlank()) {
            try {
                FilterParser.parse(text);
            } catch (FilterException e) {
                error = e.getMessage();
                errorPosition = e.position();
            }
        }
        updateSave();
    }

    /** Newlines from the multi-line box become spaces (the parser treats them alike; item tooltips stay on one line). */
    private String normalized() {
        return expression.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private void updateSave() {
        if (saveButton != null) {
            saveButton.active = error == null;
        }
    }

    private void save() {
        if (error != null) {
            return;
        }
        String text = normalized();
        if (onSave != null) {
            onSave.accept(text);
            minecraft.setScreen(parent);
            return;
        }
        if (hand != null) {
            Services.PLATFORM.sendToServer(new Payloads.SetFilter(hand == InteractionHand.MAIN_HAND, text));
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        if (previewDelay > 0 && --previewDelay == 0) {
            refreshPreview();
        }
    }

    private void refreshPreview() {
        String text = normalized();
        ItemFilter filter = null;
        if (!text.isBlank() && error == null) {
            try {
                filter = FilterParser.parse(text);
            } catch (FilterException e) {
                filter = null;
            }
        }
        preview.update(filter, minecraft.player);
        gridScroll = 0;
    }

    private void insert(String snippet) {
        if (editor == null) {
            return;
        }
        if (expression.isBlank()) {
            editor.setValue(snippet);
        } else {
            setFocused(editor);
            editor.setFocused(true);
            for (char c : snippet.toCharArray()) {
                editor.charTyped(c, 0);
            }
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ------------------------------------------------------------------ Rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        super.render(g, mouseX, mouseY, partialTick);
        ItemStack hovered = hoveredStack(mouseX, mouseY);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Theme.panel(g, px, py, pw, ph, Theme.GOLD);
        Component heading = hand != null && minecraft.player != null
                ? minecraft.player.getItemInHand(hand).getHoverName() : title;
        g.drawString(font, heading, px + 10, py + 8, Theme.GOLD, false);
        g.drawString(font, t("expression"), px + 8, py + 20, Theme.MUTED, false);
        renderStatus(g);
        renderGrid(g, mouseX, mouseY);
        renderReference(g, mouseX, mouseY);
    }

    private void renderStatus(GuiGraphics g) {
        if (editor == null) {
            return;
        }
        int x = px + 8;
        int y = editor.getY() + editor.getHeight() + 4;
        String text = normalized();
        if (text.isBlank()) {
            g.drawString(font, t("empty"), x, y, Theme.MUTED, false);
        } else if (error == null) {
            g.drawString(font, Component.literal("✔ ").append(t("valid")), x, y, Theme.SAGE, false);
        } else {
            String message = font.plainSubstrByWidth(t("invalid", error).getString(), leftW);
            g.drawString(font, message, x, y, Theme.CRIMSON, false);
            if (errorPosition >= 0 && errorPosition <= text.length()) {
                renderErrorSnippet(g, text, x, y + 11);
            }
        }
    }

    /** Shows the text around the error with the offending character highlighted. */
    private void renderErrorSnippet(GuiGraphics g, String text, int x, int y) {
        int pos = Math.min(errorPosition, text.length());
        int start = Math.max(0, pos - 30);
        int end = Math.min(text.length(), pos + 30);
        String before = (start > 0 ? "…" : "") + text.substring(start, pos);
        String at = pos < text.length() ? text.substring(pos, pos + 1) : " ";
        String after = pos + 1 <= end ? text.substring(Math.min(pos + 1, end), end) + (end < text.length() ? "…" : "") : "";
        int cx = x;
        g.drawString(font, before, cx, y, Theme.MUTED, false);
        cx += font.width(before);
        int aw = Math.max(4, font.width(at));
        g.fill(cx - 1, y - 1, cx + aw + 1, y + 9, Theme.withAlpha(Theme.CRIMSON, 0x90));
        g.drawString(font, at, cx, y, Theme.TITLE, false);
        cx += aw;
        g.drawString(font, font.plainSubstrByWidth(after, Math.max(0, px + 8 + leftW - cx)), cx, y, Theme.MUTED, false);
    }

    private int columns() {
        return Math.max(1, (leftW - 6) / CELL);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int x = px + 8;
        int headerY = gridTop - 12;
        Component count = preview.total() > preview.shown().size()
                ? t("matches", preview.total(), preview.shown().size())
                : t("matches_all", preview.total());
        g.drawString(font, t("preview"), x, headerY, Theme.GOLD, false);
        g.drawString(font, count, x + leftW - font.width(count), headerY, Theme.MUTED, false);
        g.fill(x, gridTop, x + leftW, gridBottom, 0x40000000);
        Theme.outline(g, x, gridTop, leftW, gridBottom - gridTop, Theme.LINE_SOFT);
        List<ItemStack> stacks = preview.shown();
        if (stacks.isEmpty()) {
            Component message = normalized().isBlank() || error != null ? t("preview_waiting") : t("no_matches");
            List<FormattedCharSequence> lines = font.split(message, leftW - 16);
            int ly = gridTop + 8;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 8, ly, Theme.FAINT, false);
                ly += 10;
            }
            return;
        }
        int cols = columns();
        int rows = (stacks.size() + cols - 1) / cols;
        int content = rows * CELL + 4;
        int visible = gridBottom - gridTop;
        gridScroll = Math.max(0, Math.min(gridScroll, Math.max(0, content - visible)));
        int ox = x + 3 + (leftW - 6 - cols * CELL) / 2;
        g.enableScissor(x + 1, gridTop + 1, x + leftW - 1, gridBottom - 1);
        for (int i = 0; i < stacks.size(); i++) {
            int sx = ox + (i % cols) * CELL;
            int sy = gridTop + 3 + (i / cols) * CELL - (int) gridScroll;
            if (sy + CELL < gridTop || sy > gridBottom) {
                continue;
            }
            boolean hovered = mouseX >= sx && mouseX < sx + CELL && mouseY >= Math.max(sy, gridTop) && mouseY < Math.min(sy + CELL, gridBottom);
            g.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, hovered ? 0x40FFFFFF : 0x18FFFFFF);
            ItemStack stack = stacks.get(i);
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(font, stack, sx + 1, sy + 1);
        }
        g.disableScissor();
        if (content > visible) {
            int thumb = Math.max(12, visible * visible / content);
            int ty = gridTop + (int) ((visible - thumb) * (gridScroll / (content - visible)));
            g.fill(x + leftW - 3, gridTop, x + leftW - 1, gridBottom, 0x30FFFFFF);
            g.fill(x + leftW - 3, ty, x + leftW - 1, ty + thumb, Theme.withAlpha(Theme.GOLD, 0xC0));
        }
        if (preview.fromInventory() > 0) {
            g.drawString(font, font.plainSubstrByWidth(t("preview_note").getString(), leftW), x, gridBottom + 3, Theme.FAINT, false);
        }
    }

    private ItemStack hoveredStack(int mouseX, int mouseY) {
        int x = px + 8;
        if (mouseX < x || mouseX >= x + leftW || mouseY < gridTop || mouseY >= gridBottom) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> stacks = preview.shown();
        int cols = columns();
        int ox = x + 3 + (leftW - 6 - cols * CELL) / 2;
        int col = (mouseX - ox) / CELL;
        int row = (int) ((mouseY - gridTop - 3 + gridScroll) / CELL);
        if (mouseX < ox || col < 0 || col >= cols || row < 0) {
            return ItemStack.EMPTY;
        }
        int index = row * cols + col;
        return index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }

    private void renderReference(GuiGraphics g, int mouseX, int mouseY) {
        int top = py + 20;
        int bottom = py + ph - 32;
        g.drawString(font, t("reference"), refX, top, Theme.GOLD, false);
        Component hint = t("click_insert");
        g.drawString(font, hint, refX + refW - font.width(hint), top, Theme.FAINT, false);
        top += 12;
        g.fill(refX, top, refX + refW, bottom, 0x40000000);
        Theme.outline(g, refX, top, refW, bottom - top, Theme.LINE_SOFT);
        int entryH = 22;
        int content = reference.size() * entryH + 4;
        int visible = bottom - top;
        refScroll = Math.max(0, Math.min(refScroll, Math.max(0, content - visible)));
        g.enableScissor(refX + 1, top + 1, refX + refW - 1, bottom - 1);
        int y = top + 3 - (int) refScroll;
        for (int i = 0; i < reference.size(); i++) {
            RefEntry entry = reference.get(i);
            if (y + entryH >= top && y <= bottom) {
                int hy = Math.max(y, top);
                int hb = Math.min(y + entryH, bottom);
                boolean hovered = mouseX >= refX && mouseX < refX + refW && mouseY >= hy && mouseY < hb;
                if (hovered) {
                    g.fill(refX + 1, y, refX + refW - 1, y + entryH - 1, 0x22FFFFFF);
                }
                if (i == 3) {
                    g.fill(refX + 4, y - 2, refX + refW - 4, y - 1, Theme.LINE);
                }
                g.drawString(font, font.plainSubstrByWidth(entry.title().getString(), refW - 10), refX + 5, y + 1, i < 3 ? Theme.SKY : Theme.TEXT, false);
                g.drawString(font, font.plainSubstrByWidth(entry.detail().getString(), refW - 14), refX + 9, y + 11, Theme.MUTED, false);
                String insert = entry.insert();
                if (hb > hy) {
                    hits.add(new Hit(refX, hy, refW, hb - hy, () -> insert(insert)));
                }
            }
            y += entryH;
        }
        g.disableScissor();
        if (content > visible) {
            int thumb = Math.max(12, visible * visible / content);
            int ty = top + (int) ((visible - thumb) * (refScroll / (content - visible)));
            g.fill(refX + refW - 3, top, refX + refW - 1, bottom, 0x30FFFFFF);
            g.fill(refX + refW - 3, ty, refX + refW - 1, ty + thumb, Theme.withAlpha(Theme.GOLD, 0xC0));
        }
    }

    // ------------------------------------------------------------------ Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            for (Hit hit : List.copyOf(hits)) {
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= refX && mouseX < refX + refW && mouseY >= py + 32 && mouseY < py + ph - 32) {
            refScroll = Math.max(0, refScroll - scrollY * 22);
            return true;
        }
        if (mouseX >= px + 8 && mouseX < px + 8 + leftW && mouseY >= gridTop && mouseY < gridBottom) {
            gridScroll = Math.max(0, gridScroll - scrollY * CELL);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Enter / Cmd+Enter saves without leaving the keyboard.
        if ((keyCode == 257 || keyCode == 335) && hasControlDown()) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
