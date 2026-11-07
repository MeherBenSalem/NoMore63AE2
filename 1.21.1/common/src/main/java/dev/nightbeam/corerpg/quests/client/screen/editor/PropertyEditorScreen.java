package dev.nightbeam.corerpg.quests.client.screen.editor;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.QuestObject;
import dev.nightbeam.corerpg.quests.book.RewardTable;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.screen.EditorActions;
import dev.nightbeam.corerpg.quests.client.screen.FilterEditorScreen;
import dev.nightbeam.corerpg.quests.client.widget.ThemedButton;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.schema.EditorKind;
import dev.nightbeam.corerpg.quests.schema.Prop;
import dev.nightbeam.corerpg.quests.schema.PropType;
import dev.nightbeam.corerpg.quests.schema.PropTypes;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Edits any quest object. The rows come from the object's property schema, so every field of every task and reward
 * type is editable without a hand-written screen. Changes apply live (you see them on the canvas) and are sent to
 * the server on Save; Cancel restores the original values.
 */
public class PropertyEditorScreen extends Screen {
    private static final int ROW = 22;

    private final Screen parent;
    private final QuestObject object;
    private final JsonObject snapshot;
    private final Props props;
    private final List<String> categories;
    private String category;
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int labelWidth;
    private @Nullable EditBox weightBox;
    private double originalWeight = -1;
    private ThemedButton saveButton;
    private @Nullable String error;
    private final @Nullable Consumer<QuestObject> afterSave;

    private final class Row {
        final Prop<?> prop;
        final List<AbstractWidget> widgets = new ArrayList<>();
        int y;

        Row(Prop<?> prop) {
            this.prop = prop;
        }
    }

    public PropertyEditorScreen(Screen parent, QuestObject object) {
        this(parent, object, null);
    }

    public PropertyEditorScreen(Screen parent, QuestObject object, @Nullable Consumer<QuestObject> afterSave) {
        super(Component.translatable("corerpg_quests.gui.editing", object.displayTitle()));
        this.parent = parent;
        this.object = object;
        this.afterSave = afterSave;
        this.snapshot = object.propsToJson(ClientQuests.ctx());
        this.props = object.props();
        this.categories = new ArrayList<>();
        for (String c : props.categories()) {
            boolean any = false;
            for (Prop<?> p : props) {
                if (p.category().equals(c) && !p.editorHidden()) {
                    any = true;
                }
            }
            if (any) {
                categories.add(c);
            }
        }
        this.category = categories.isEmpty() ? "general" : categories.getFirst();
        if (object instanceof Reward r && r.table != null) {
            for (RewardTable.Entry e : r.table.entries) {
                if (e.reward == r) {
                    originalWeight = e.weight;
                }
            }
        }
    }

    static Component label(String key) {
        String k = "corerpg_quests.prop." + key;
        if (Language.getInstance().has(k)) {
            return Component.translatable(k);
        }
        return Component.literal(pretty(key));
    }

    static @Nullable Component tooltipFor(String key) {
        String k = "corerpg_quests.prop." + key + ".tooltip";
        return Language.getInstance().has(k) ? Component.translatable(k) : null;
    }

    static Component categoryLabel(String key) {
        String k = "corerpg_quests.category." + key;
        return Language.getInstance().has(k) ? Component.translatable(k) : Component.literal(pretty(key));
    }

    static Component enumLabel(StringRepresentable value) {
        String k = "corerpg_quests.enum." + value.getSerializedName();
        return Language.getInstance().has(k) ? Component.translatable(k) : Component.literal(pretty(value.getSerializedName()));
    }

    static String pretty(String key) {
        String s = key.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(460, width - 20);
        panelHeight = Math.min(360, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        labelWidth = Math.min(150, (panelWidth - 110) / 2);
        rebuildRows();
        int by = top + panelHeight - 22;
        saveButton = addRenderableWidget(new ThemedButton(left + panelWidth - 154, by, 70, 16, Component.translatable("corerpg_quests.gui.save"), b -> save(), Theme.SAGE));
        addRenderableWidget(new ThemedButton(left + panelWidth - 78, by, 70, 16, Component.translatable("gui.cancel"), b -> cancel(), Theme.MUTED));
        if (originalWeight >= 0) {
            weightBox = new EditBox(font, left + 60, by + 1, 60, 14, Component.translatable("corerpg_quests.gui.weight"));
            weightBox.setValue(trimNumber(currentWeight()));
            weightBox.setFilter(s -> s.matches("[0-9.]*"));
            addRenderableWidget(weightBox);
        }
        validate();
    }

    private double currentWeight() {
        if (object instanceof Reward r && r.table != null) {
            for (RewardTable.Entry e : r.table.entries) {
                if (e.reward == r) {
                    return e.weight;
                }
            }
        }
        return 1;
    }

    private static String trimNumber(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private int listTop() {
        return top + 24;
    }

    private int listBottom() {
        return top + panelHeight - 28;
    }

    private int listLeft() {
        return left + 96;
    }

    private void rebuildRows() {
        for (Row row : rows) {
            row.widgets.forEach(this::removeWidget);
        }
        rows.clear();
        for (Prop<?> prop : props) {
            if (prop.editorHidden() || !prop.category().equals(category)) {
                continue;
            }
            Row row = new Row(prop);
            buildWidgets(row);
            row.widgets.forEach(this::addWidget);
            rows.add(row);
        }
        layoutRows();
    }

    private void layoutRows() {
        int y = listTop() + 4 - (int) scroll;
        for (Row row : rows) {
            row.y = y;
            int x = listLeft() + labelWidth + 6;
            for (AbstractWidget w : row.widgets) {
                w.setX(x);
                w.setY(y + 2);
                x += w.getWidth() + 3;
                w.visible = y + 2 >= listTop() && y + 18 <= listBottom();
            }
            y += ROW;
        }
    }

    private int valueWidth() {
        return left + panelWidth - 10 - (listLeft() + labelWidth + 6);
    }

    @SuppressWarnings("unchecked")
    private void buildWidgets(Row row) {
        Prop<Object> prop = (Prop<Object>) row.prop;
        PropType<Object> type = prop.type();
        EditorKind kind = type.editorKind();
        int w = valueWidth();
        switch (kind) {
            case BOOL -> row.widgets.add(cycle(w, List.of(Boolean.TRUE, Boolean.FALSE), prop,
                    v -> Component.translatable((Boolean) v ? "corerpg_quests.gui.yes" : "corerpg_quests.gui.no")));
            case ENUM -> {
                Object[] values = ((PropTypes.EnumType<?>) (Object) type).values();
                row.widgets.add(cycle(w, Arrays.asList(values), prop, v -> enumLabel((StringRepresentable) v)));
            }
            case STRING -> row.widgets.add(text(w, prop, s -> s));
            case INT -> row.widgets.add(text(w, prop, Integer::parseInt));
            case LONG -> row.widgets.add(text(w, prop, Long::parseLong));
            case DOUBLE -> row.widgets.add(text(w, prop, Double::parseDouble));
            case COLOR -> row.widgets.add(text(w, prop, s -> Integer.parseUnsignedInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF));
            case STRING_LIST -> row.widgets.add(text(w, prop, s -> {
                List<String> list = new ArrayList<>();
                for (String part : s.split(",")) {
                    if (!part.isBlank()) {
                        list.add(part.trim());
                    }
                }
                return list;
            }));
            case FILTER -> {
                EditBox box = text(w - 22, prop, s -> s);
                row.widgets.add(box);
                row.widgets.add(small("…", Component.translatable("corerpg_quests.gui.open_filter_editor"), () ->
                        minecraft.setScreen(FilterEditorScreen.forExpression(this, (String) prop.get(), value -> {
                            prop.set(value);
                            box.setValue(value);
                        }))));
            }
            case ID_REF -> {
                EditBox box = text(w - 22, prop, s -> s);
                row.widgets.add(box);
                row.widgets.add(small("…", Component.translatable("corerpg_quests.gui.pick"), () -> pickId(prop.key(), id -> {
                    prop.set(id);
                    box.setValue(id);
                })));
            }
            case RESOURCE -> {
                Object rawType = type;
                ResourceKind rk = rawType instanceof PropTypes.ResourceType rt ? rt.kind() : rawType instanceof PropTypes.RefType ft ? ft.kind() : ResourceKind.ANY;
                boolean ref = rawType instanceof PropTypes.RefType;
                EditBox box = text(w - 22, prop, s -> {
                    if (ref) {
                        if (s.isBlank()) {
                            return null;
                        }
                        Ref r = Ref.parse(s);
                        if (r == null) {
                            throw new IllegalArgumentException();
                        }
                        return r;
                    }
                    ResourceLocation id = ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
                    if (id == null) {
                        throw new IllegalArgumentException();
                    }
                    return id;
                });
                row.widgets.add(box);
                row.widgets.add(small("…", Component.translatable("corerpg_quests.gui.pick"), () ->
                        minecraft.setScreen(PickerScreen.resources(this, rk, ref, id -> box.setValue(id)))));
            }
            case TEXT -> row.widgets.add(new ThemedButton(0, 0, w, 16, textSummary((List<String>) prop.get()), b ->
                    minecraft.setScreen(new TextEditScreen(this, label(prop.key()), (List<String>) prop.get(), lines -> {
                        prop.set(lines);
                        b.setMessage(textSummary(lines));
                    })), Theme.GOLD));
            case ID_LIST -> row.widgets.add(new ThemedButton(0, 0, w, 16, idSummary((List<String>) prop.get()), b ->
                    minecraft.setScreen(new IdListScreen(this, label(prop.key()), (List<String>) prop.get(), object, ids -> {
                        prop.set(ids);
                        b.setMessage(idSummary(ids));
                    })), Theme.GOLD));
            case ITEM_STACK -> {
                ItemSlot slot = new ItemSlot(() -> (ItemStack) prop.get(), stack -> prop.set(stack), () ->
                        minecraft.setScreen(PickerScreen.items(this, stack -> prop.set(stack))));
                row.widgets.add(slot);
                row.widgets.add(small("✋", Component.translatable("corerpg_quests.gui.from_hand"), () -> {
                    if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
                        prop.set(minecraft.player.getMainHandItem().copyWithCount(1));
                    }
                }));
                row.widgets.add(small("✕", Component.translatable("corerpg_quests.gui.clear"), () -> prop.set(ItemStack.EMPTY)));
            }
            case ITEM_LIST -> {
                List<ItemStack> current = (List<ItemStack>) prop.get();
                row.widgets.add(new ThemedButton(0, 0, w - 44, 16, Component.translatable("corerpg_quests.gui.items_count", current.size()), b -> { }, Theme.MUTED));
                row.widgets.add(small("+", Component.translatable("corerpg_quests.gui.add_item"), () -> minecraft.setScreen(PickerScreen.items(this, stack -> {
                    List<ItemStack> list = new ArrayList<>((List<ItemStack>) prop.get());
                    list.add(stack);
                    prop.set(list);
                }))));
                row.widgets.add(small("✕", Component.translatable("corerpg_quests.gui.clear"), () -> prop.set(new ArrayList<>())));
            }
            case ICON -> {
                EditBox box = text(w - 44, prop, s -> {
                    if (s.isBlank()) {
                        return Icon.EMPTY;
                    }
                    if (s.startsWith("texture:")) {
                        ResourceLocation id = ResourceLocation.tryParse(s.substring(8));
                        if (id == null) {
                            throw new IllegalArgumentException();
                        }
                        return Icon.texture(id);
                    }
                    ResourceLocation id = ResourceLocation.tryParse(s);
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                        throw new IllegalArgumentException();
                    }
                    return Icon.of(BuiltInRegistries.ITEM.get(id));
                });
                row.widgets.add(box);
                row.widgets.add(small("…", Component.translatable("corerpg_quests.gui.pick_item"), () -> minecraft.setScreen(PickerScreen.items(this, stack -> {
                    prop.set(Icon.of(stack));
                    box.setValue(display(prop));
                }))));
                row.widgets.add(small("✋", Component.translatable("corerpg_quests.gui.from_hand"), () -> {
                    if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
                        prop.set(Icon.of(minecraft.player.getMainHandItem()));
                        box.setValue(display(prop));
                    }
                }));
            }
        }
    }

    private static Component textSummary(List<String> lines) {
        if (lines.isEmpty() || TextUtil.isBlank(lines)) {
            return Component.translatable("corerpg_quests.gui.text_empty");
        }
        String first = TextUtil.plain(lines.getFirst());
        if (first.length() > 30) {
            first = first.substring(0, 28) + "…";
        }
        return Component.literal(first + (lines.size() > 1 ? "  (+" + (lines.size() - 1) + ")" : ""));
    }

    private static Component idSummary(List<String> ids) {
        return ids.isEmpty() ? Component.translatable("corerpg_quests.gui.none") : Component.translatable("corerpg_quests.gui.selected", ids.size());
    }

    private ThemedButton small(String label, Component tooltip, Runnable action) {
        ThemedButton b = new ThemedButton(0, 0, 18, 16, Component.literal(label), btn -> action.run(), Theme.GOLD);
        b.setTooltip(Tooltip.create(tooltip));
        return b;
    }

    private ThemedButton cycle(int w, List<Object> values, Prop<Object> prop, java.util.function.Function<Object, Component> labeler) {
        return new ThemedButton(0, 0, w, 16, labeler.apply(prop.get()), b -> {
            int i = values.indexOf(prop.get());
            int next = (i + (hasShiftDown() ? values.size() - 1 : 1)) % values.size();
            prop.set(values.get(next));
            b.setMessage(labeler.apply(prop.get()));
            validate();
        }, Theme.GOLD);
    }

    private EditBox text(int w, Prop<Object> prop, ValueParser parser) {
        EditBox box = new EditBox(font, 0, 0, w, 16, label(prop.key()));
        box.setMaxLength(32000);
        box.setValue(display(prop));
        box.moveCursorToStart(false);
        box.setResponder(s -> {
            try {
                Object value = parser.parse(s.trim());
                String problem = prop.type().validate(value);
                if (problem != null) {
                    box.setTextColor(0xFFFF6B6B);
                    box.setTooltip(Tooltip.create(Component.literal(problem)));
                } else {
                    prop.set(value);
                    box.setTextColor(0xFFE0E0E0);
                    Component tip = tooltipFor(prop.key());
                    box.setTooltip(tip == null ? null : Tooltip.create(tip));
                }
            } catch (RuntimeException e) {
                box.setTextColor(0xFFFF6B6B);
            }
            validate();
        });
        Component tip = tooltipFor(prop.key());
        if (tip != null) {
            box.setTooltip(Tooltip.create(tip));
        }
        return box;
    }

    @FunctionalInterface
    private interface ValueParser {
        Object parse(String text);
    }

    private static String display(Prop<?> prop) {
        Object value = prop.get();
        return switch (prop.type().editorKind()) {
            case COLOR -> String.format(Locale.ROOT, "#%06X", ((Integer) value) & 0xFFFFFF);
            case STRING_LIST -> String.join(", ", castList(value));
            case DOUBLE -> trimNumber((Double) value);
            case ICON -> {
                Icon icon = (Icon) value;
                if (icon.texture() != null) {
                    yield "texture:" + icon.texture();
                }
                yield icon.stack().isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(icon.stack().getItem()).toString();
            }
            case RESOURCE -> value == null ? "" : (Object) prop.type() instanceof PropTypes.ResourceType rt && rt.kind().tag ? "#" + value : value.toString();
            default -> value == null ? "" : value.toString();
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    private void pickId(String key, Consumer<String> onPick) {
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return;
        }
        List<PickerScreen.Option> options = new ArrayList<>();
        switch (key) {
            case "group" -> {
                options.add(PickerScreen.Option.of("", Component.translatable("corerpg_quests.gui.no_group"), Icon.EMPTY));
                book.groups.forEach(g -> options.add(PickerScreen.Option.of(g.id(), g.displayTitle(), g.displayIcon())));
            }
            case "table" -> book.tables.forEach(t -> options.add(PickerScreen.Option.of(t.id(), t.displayTitle(), t.displayIcon())));
            case "autofocus" -> {
                options.add(PickerScreen.Option.of("", Component.translatable("corerpg_quests.gui.none"), Icon.EMPTY));
                if (object instanceof Chapter c) {
                    c.quests.forEach(q -> options.add(PickerScreen.Option.of(q.id(), q.displayTitle(), q.displayIcon())));
                }
            }
            default -> {
                for (Chapter c : book.chapters) {
                    for (Quest q : c.quests) {
                        options.add(PickerScreen.Option.of(q.id(), q.displayTitle(), q.displayIcon()));
                    }
                }
            }
        }
        minecraft.setScreen(new PickerScreen(this, Component.translatable("corerpg_quests.gui.pick"), options, false, true, o -> onPick.accept(o.id())));
    }

    private void validate() {
        error = null;
        for (Prop<?> p : props) {
            String problem = validateProp(p);
            if (problem != null) {
                error = label(p.key()).getString() + ": " + problem;
                break;
            }
        }
        if (saveButton != null) {
            saveButton.active = error == null && ClientQuests.editing();
        }
    }

    @SuppressWarnings("unchecked")
    private static String validateProp(Prop<?> p) {
        Prop<Object> prop = (Prop<Object>) p;
        return prop.type().validate(prop.get());
    }

    private void save() {
        validate();
        if (error != null) {
            return;
        }
        JsonObject data = object.propsToJson(ClientQuests.ctx());
        if (weightBox != null) {
            try {
                data.addProperty("weight", Math.max(0, Double.parseDouble(weightBox.getValue())));
            } catch (NumberFormatException ignored) {
                data.addProperty("weight", originalWeight);
            }
        }
        if (!data.equals(snapshot) || weightBox != null) {
            EditorActions.update(object.id(), data);
        }
        // Keep the original values locally until the server echoes the edit, so nothing changes twice.
        object.readProps(snapshot, ClientQuests.ctx());
        minecraft.setScreen(parent);
        if (afterSave != null) {
            afterSave.accept(object);
        }
    }

    private void cancel() {
        object.readProps(snapshot, ClientQuests.ctx());
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Drawn first in render(); Screen.render() calls this again after our panel, so it must stay empty.
    }

    private void drawOverlay(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layoutRows();
        drawOverlay(g);
        Theme.panel(g, left, top, panelWidth, panelHeight, Theme.GOLD);
        IconRenderer.render(g, object.displayIcon(), left + 6, top + 5, 12);
        g.drawString(font, font.substrByWidth(Component.translatable("corerpg_quests.gui.editing_kind",
                Component.translatable("corerpg_quests.kind." + object.kind().getSerializedName()), object.displayTitle()), panelWidth - 120).getString(),
                left + 22, top + 7, Theme.GOLD, false);
        String id = object.id();
        g.drawString(font, id, left + panelWidth - 8 - font.width(id), top + 7, Theme.FAINT, false);
        g.fill(left + 4, top + 19, left + panelWidth - 4, top + 20, Theme.LINE);

        // Category tabs
        int ty = listTop() + 2;
        for (String c : categories) {
            boolean selected = c.equals(category);
            boolean hover = mouseX >= left + 4 && mouseX < listLeft() - 4 && mouseY >= ty && mouseY < ty + 16;
            if (selected) {
                g.fill(left + 4, ty, listLeft() - 4, ty + 16, 0x50E8C15A);
                g.fill(left + 4, ty, left + 6, ty + 16, Theme.GOLD);
            } else if (hover) {
                g.fill(left + 4, ty, listLeft() - 4, ty + 16, 0x20FFFFFF);
            }
            g.drawString(font, font.substrByWidth(categoryLabel(c), listLeft() - left - 16).getString(), left + 10, ty + 4, selected ? Theme.TITLE : Theme.TEXT, false);
            ty += 18;
        }
        g.fill(listLeft() - 2, listTop(), listLeft() - 1, listBottom(), Theme.LINE);

        // Rows
        g.enableScissor(listLeft(), listTop(), left + panelWidth - 2, listBottom());
        for (Row row : rows) {
            if (row.y + ROW < listTop() || row.y > listBottom()) {
                continue;
            }
            boolean isDefault = row.prop.isDefault();
            g.drawString(font, font.substrByWidth(label(row.prop.key()), labelWidth - 6).getString(), listLeft() + 6, row.y + 6,
                    isDefault ? Theme.TEXT : Theme.GOLD, false);
            if (!isDefault) {
                g.fill(listLeft() + 2, row.y + 5, listLeft() + 4, row.y + 15, Theme.GOLD);
            }
            if (row.prop.type().editorKind() == EditorKind.ITEM_LIST) {
                @SuppressWarnings("unchecked")
                List<ItemStack> items = (List<ItemStack>) row.prop.get();
                int ix = row.widgets.getFirst().getX() + 4;
                for (int i = 0; i < Math.min(items.size(), 5); i++) {
                    g.renderItem(items.get(i), ix + i * 17, row.y + 2);
                }
            }
        }
        for (Row row : rows) {
            for (AbstractWidget w : row.widgets) {
                if (w.visible) {
                    w.render(g, mouseX, mouseY, partialTick);
                }
            }
        }
        g.disableScissor();
        int contentHeight = rows.size() * ROW + 8;
        int view = listBottom() - listTop();
        if (contentHeight > view) {
            int barH = Math.max(12, view * view / contentHeight);
            int barY = listTop() + (int) ((view - barH) * scroll / Math.max(1, contentHeight - view));
            g.fill(left + panelWidth - 5, barY, left + panelWidth - 3, barY + barH, Theme.LINE);
        }

        // Footer
        g.fill(left + 4, listBottom() + 2, left + panelWidth - 4, listBottom() + 3, Theme.LINE);
        if (weightBox != null) {
            g.drawString(font, Component.translatable("corerpg_quests.gui.weight"), left + 10, top + panelHeight - 18, Theme.MUTED, false);
        }
        if (error != null) {
            g.drawString(font, font.plainSubstrByWidth(error, panelWidth - 180), left + (weightBox != null ? 130 : 10), top + panelHeight - 18, Theme.CRIMSON, false);
        } else if (!ClientQuests.editing()) {
            g.drawString(font, Component.translatable("corerpg_quests.gui.not_editing"), left + 10, top + panelHeight - 18, Theme.CRIMSON, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ty = listTop() + 2;
        for (String c : categories) {
            if (mx >= left + 4 && mx < listLeft() - 4 && my >= ty && my < ty + 16) {
                if (!c.equals(category)) {
                    category = c;
                    scroll = 0;
                    rebuildRows();
                }
                return true;
            }
            ty += 18;
        }
        if (my >= listTop() && my < listBottom()) {
            for (Row row : rows) {
                for (AbstractWidget w : row.widgets) {
                    if (w.visible && w.isMouseOver(mx, my) && w.mouseClicked(mx, my, button)) {
                        setFocused(w);
                        return true;
                    }
                }
            }
        }
        for (Row row : rows) {
            for (AbstractWidget w : row.widgets) {
                if (w instanceof EditBox box) {
                    box.setFocused(false);
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int contentHeight = rows.size() * ROW + 8;
        int max = Math.max(0, contentHeight - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(max, scroll - sy * ROW));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER && hasControlDown()) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** A clickable item slot: left-click opens the picker, right-click clears. */
    private static final class ItemSlot extends AbstractWidget {
        private final java.util.function.Supplier<ItemStack> getter;
        private final Consumer<ItemStack> setter;
        private final Runnable pick;

        ItemSlot(java.util.function.Supplier<ItemStack> getter, Consumer<ItemStack> setter, Runnable pick) {
            super(0, 0, 18, 18, Component.empty());
            this.getter = getter;
            this.setter = setter;
            this.pick = pick;
            setTooltip(Tooltip.create(Component.translatable("corerpg_quests.gui.item_slot_hint")));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(getX(), getY() - 1, getX() + 18, getY() + 17, isHovered() ? 0xFF4A3A32 : 0xFF241C1C);
            Theme.outline(g, getX(), getY() - 1, 18, 18, isHovered() ? Theme.GOLD : Theme.LINE);
            ItemStack stack = getter.get();
            if (!stack.isEmpty()) {
                g.renderItem(stack, getX() + 1, getY());
                if (isHovered()) {
                    g.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, stack, mouseX, mouseY);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!isMouseOver(mx, my)) {
                return false;
            }
            if (button == 1) {
                setter.accept(ItemStack.EMPTY);
            } else {
                pick.run();
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }
}
