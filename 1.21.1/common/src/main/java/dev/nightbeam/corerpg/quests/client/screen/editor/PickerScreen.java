package dev.nightbeam.corerpg.quests.client.screen.editor;

import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.client.ClientQuests;
import dev.nightbeam.corerpg.quests.client.render.IconRenderer;
import dev.nightbeam.corerpg.quests.client.render.Theme;
import dev.nightbeam.corerpg.quests.client.widget.ThemedButton;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** A searchable list (or item grid) to pick one option from. */
public class PickerScreen extends Screen {
    public record Option(String id, Component label, Icon icon, String search, @Nullable ItemStack stack) {
        public static Option of(String id, Component label, Icon icon) {
            return new Option(id, label, icon, (TextUtil.plain(label.getString()) + " " + id).toLowerCase(Locale.ROOT), null);
        }
    }

    private final Screen parent;
    private final List<Option> options;
    private final Consumer<Option> onPick;
    private final boolean grid;
    private final boolean allowCustom;
    private final List<Option> filtered = new ArrayList<>();
    private EditBox search;
    private double scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public PickerScreen(Screen parent, Component title, List<Option> options, boolean grid, boolean allowCustom, Consumer<Option> onPick) {
        super(title);
        this.parent = parent;
        this.options = options;
        this.onPick = onPick;
        this.grid = grid;
        this.allowCustom = allowCustom;
    }

    // ---------------------------------------------------------------- Factories

    public static PickerScreen quests(Screen parent, Component title, QuestBook book, Consumer<Quest> onPick) {
        List<Option> options = new ArrayList<>();
        for (Chapter chapter : book.chapters) {
            for (Quest q : chapter.quests) {
                Component label = q.displayTitle().copy().append(Component.literal("  " + TextUtil.plain(chapter.displayTitle().getString())).withStyle(ChatFormatting.DARK_GRAY));
                options.add(new Option(q.id(), label, q.displayIcon(), (TextUtil.plain(q.displayTitle().getString()) + " " + q.id() + " " + chapter.id()).toLowerCase(Locale.ROOT), null));
            }
        }
        return new PickerScreen(parent, title, options, false, false, o -> {
            Quest q = book.quest(o.id());
            if (q != null) {
                onPick.accept(q);
            }
        });
    }

    public static PickerScreen items(Screen parent, Consumer<ItemStack> onPick) {
        List<Option> options = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        Set<String> seen = new LinkedHashSet<>();
        if (mc.player != null) {
            for (ItemStack stack : mc.player.getInventory().items) {
                if (!stack.isEmpty()) {
                    ItemStack copy = stack.copyWithCount(1);
                    String key = BuiltInRegistries.ITEM.getKey(copy.getItem()) + copy.getComponentsPatch().toString();
                    if (seen.add(key)) {
                        options.add(new Option(BuiltInRegistries.ITEM.getKey(copy.getItem()).toString(), copy.getHoverName(), Icon.of(copy),
                                (copy.getHoverName().getString() + " " + BuiltInRegistries.ITEM.getKey(copy.getItem())).toLowerCase(Locale.ROOT), copy));
                    }
                }
            }
        }
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            options.add(new Option(id.toString(), stack.getHoverName(), Icon.of(stack),
                    (stack.getHoverName().getString() + " " + id).toLowerCase(Locale.ROOT), stack));
        }
        return new PickerScreen(parent, Component.translatable("corerpg_quests.gui.pick_item"), options, true, false, o -> {
            if (o.stack() != null) {
                onPick.accept(o.stack().copy());
            }
        });
    }

    public static PickerScreen strings(Screen parent, Component title, List<String> ids, boolean allowCustom, Consumer<String> onPick) {
        List<Option> options = new ArrayList<>();
        for (String id : ids) {
            options.add(Option.of(id, Component.literal(id), Icon.EMPTY));
        }
        return new PickerScreen(parent, title, options, false, allowCustom, o -> onPick.accept(o.id()));
    }

    /** Suggestions for a registry id or tag of the given kind. */
    public static PickerScreen resources(Screen parent, ResourceKind kind, boolean tags, Consumer<String> onPick) {
        List<Option> options = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case ITEM -> registry(options, BuiltInRegistries.ITEM, tags, id -> Icon.of(BuiltInRegistries.ITEM.get(id)));
            case BLOCK -> registry(options, BuiltInRegistries.BLOCK, tags, id -> Icon.of(BuiltInRegistries.BLOCK.get(id).asItem()));
            case FLUID -> registry(options, BuiltInRegistries.FLUID, tags, id -> Icon.of(BuiltInRegistries.FLUID.get(id).getBucket()));
            case ENTITY -> registry(options, BuiltInRegistries.ENTITY_TYPE, tags, id -> {
                SpawnEggItem egg = SpawnEggItem.byId(BuiltInRegistries.ENTITY_TYPE.get(id));
                return egg == null ? Icon.EMPTY : Icon.of(egg);
            });
            case SOUND -> registry(options, BuiltInRegistries.SOUND_EVENT, false, id -> Icon.EMPTY);
            case STAT -> registry(options, BuiltInRegistries.CUSTOM_STAT, false, id -> Icon.EMPTY);
            case BIOME -> {
                if (mc.level != null) {
                    if (tags) {
                        ClientQuests.editorList("biome_tag").forEach(id -> options.add(Option.of("#" + id, Component.literal("#" + id), Icon.EMPTY)));
                    }
                    mc.level.registryAccess().registryOrThrow(Registries.BIOME).keySet().forEach(id ->
                            options.add(Option.of(id.toString(), Component.translatable("biome." + id.getNamespace() + "." + id.getPath()), Icon.EMPTY)));
                }
            }
            case DIMENSION -> ClientQuests.editorList("dimension").forEach(id -> options.add(Option.of(id, Component.literal(id), Icon.EMPTY)));
            case STRUCTURE -> {
                if (tags) {
                    ClientQuests.editorList("structure_tag").forEach(id -> options.add(Option.of("#" + id, Component.literal("#" + id), Icon.EMPTY)));
                }
                ClientQuests.editorList("structure").forEach(id -> options.add(Option.of(id, Component.literal(id), Icon.EMPTY)));
            }
            case ADVANCEMENT -> ClientQuests.editorList("advancement").forEach(id -> options.add(Option.of(id, Component.literal(id), Icon.EMPTY)));
            case LOOT_TABLE -> ClientQuests.editorList("loot_table").forEach(id -> options.add(Option.of(id, Component.literal(id), Icon.EMPTY)));
            case ITEM_TAG -> ClientQuests.editorList("item_tag").forEach(id -> options.add(Option.of("#" + id, Component.literal("#" + id), Icon.EMPTY)));
            case BLOCK_TAG -> ClientQuests.editorList("block_tag").forEach(id -> options.add(Option.of("#" + id, Component.literal("#" + id), Icon.EMPTY)));
            case ENTITY_TAG -> ClientQuests.editorList("entity_tag").forEach(id -> options.add(Option.of("#" + id, Component.literal("#" + id), Icon.EMPTY)));
            case BIOME_TAG -> ClientQuests.editorList("biome_tag").forEach(id -> options.add(Option.of("#" + id, Component.literal("#" + id), Icon.EMPTY)));
            case STRUCTURE_TAG -> ClientQuests.editorList("structure_tag").forEach(id -> options.add(Option.of("#" + id, Component.literal("#" + id), Icon.EMPTY)));
            default -> {
            }
        }
        return new PickerScreen(parent, Component.translatable("corerpg_quests.gui.pick_id"), options, false, true, o -> onPick.accept(o.id()));
    }

    private static <T> void registry(List<Option> options, Registry<T> registry, boolean tags, java.util.function.Function<ResourceLocation, Icon> icon) {
        if (tags) {
            registry.getTagNames().map(t -> t.location().toString()).sorted().forEach(id -> options.add(Option.of("#" + id, Component.literal("#" + id), Icon.EMPTY)));
        }
        List<ResourceLocation> ids = new ArrayList<>(registry.keySet());
        ids.sort(null);
        for (ResourceLocation id : ids) {
            Component label = Component.literal(id.toString());
            T value = registry.get(id);
            if (value instanceof EntityType<?> type) {
                label = type.getDescription().copy().append(Component.literal("  " + id).withStyle(ChatFormatting.DARK_GRAY));
            } else if (value instanceof net.minecraft.world.level.block.Block block) {
                label = block.getName().copy().append(Component.literal("  " + id).withStyle(ChatFormatting.DARK_GRAY));
            }
            options.add(new Option(id.toString(), label, icon.apply(id), (label.getString() + " " + id).toLowerCase(Locale.ROOT), null));
        }
    }

    // ---------------------------------------------------------------- Screen

    @Override
    protected void init() {
        panelWidth = Math.min(grid ? 330 : 300, width - 20);
        panelHeight = Math.min(300, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        search = new EditBox(font, left + 8, top + 20, panelWidth - 16, 14, Component.translatable("corerpg_quests.gui.search"));
        search.setResponder(s -> {
            scroll = 0;
            filter();
        });
        search.setMaxLength(200);
        addRenderableWidget(search);
        setInitialFocus(search);
        int by = top + panelHeight - 22;
        if (allowCustom) {
            addRenderableWidget(new ThemedButton(left + 8, by, 110, 16, Component.translatable("corerpg_quests.gui.use_typed"), b -> {
                String text = search.getValue().trim();
                if (!text.isEmpty()) {
                    pick(Option.of(text, Component.literal(text), Icon.EMPTY));
                }
            }, Theme.GOLD));
        }
        addRenderableWidget(new ThemedButton(left + panelWidth - 78, by, 70, 16, Component.translatable("gui.cancel"), b -> onClose(), Theme.MUTED));
        filter();
    }

    private void filter() {
        filtered.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Option o : options) {
            if (q.isEmpty() || o.search().contains(q)) {
                filtered.add(o);
            }
        }
    }

    private void pick(Option option) {
        minecraft.setScreen(parent);
        onPick.accept(option);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private int listTop() {
        return top + 40;
    }

    private int listBottom() {
        return top + panelHeight - 28;
    }

    private int columns() {
        return Math.max(1, (panelWidth - 16) / 18);
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
        Theme.panel(g, left, top, panelWidth, panelHeight, Theme.EMBER);
        g.drawString(font, title, left + 8, top + 7, Theme.GOLD, false);
        String count = filtered.size() + "";
        g.drawString(font, count, left + panelWidth - 8 - font.width(count), top + 7, Theme.FAINT, false);
        int lt = listTop();
        int lb = listBottom();
        g.enableScissor(left + 2, lt, left + panelWidth - 2, lb);
        Option hovered = null;
        if (grid) {
            int cols = columns();
            int startRow = (int) (scroll / 18);
            for (int i = startRow * cols; i < filtered.size(); i++) {
                int row = i / cols;
                int col = i % cols;
                int x = left + 8 + col * 18;
                int y = lt + row * 18 - (int) scroll;
                if (y > lb) {
                    break;
                }
                Option o = filtered.get(i);
                boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18 && mouseY >= lt && mouseY < lb;
                g.fill(x, y, x + 17, y + 17, hover ? 0x60E8743B : 0x30000000);
                if (o.stack() != null) {
                    g.renderItem(o.stack(), x + 1, y + 1);
                }
                if (hover) {
                    hovered = o;
                }
            }
        } else {
            int startRow = (int) (scroll / 14);
            for (int i = startRow; i < filtered.size(); i++) {
                int y = lt + i * 14 - (int) scroll;
                if (y > lb) {
                    break;
                }
                Option o = filtered.get(i);
                boolean hover = mouseX >= left + 4 && mouseX < left + panelWidth - 4 && mouseY >= y && mouseY < y + 14 && mouseY >= lt && mouseY < lb;
                if (hover) {
                    g.fill(left + 4, y, left + panelWidth - 4, y + 14, 0x40E8743B);
                }
                int tx = left + 8;
                if (!o.icon().isEmpty()) {
                    IconRenderer.render(g, o.icon(), tx, y + 2, 10);
                    tx += 14;
                }
                g.drawString(font, font.substrByWidth(o.label(), left + panelWidth - 10 - tx).getString(), tx, y + 3, hover ? Theme.TITLE : Theme.TEXT, false);
            }
        }
        g.disableScissor();
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null && hovered.stack() != null) {
            g.renderTooltip(font, hovered.stack(), mouseX, mouseY);
        }
    }

    private int contentHeight() {
        return grid ? (filtered.size() + columns() - 1) / columns() * 18 : filtered.size() * 14;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, contentHeight() - (listBottom() - listTop()));
        scroll = Math.max(0, Math.min(max, scroll - sy * (grid ? 18 : 28)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int lt = listTop();
        int lb = listBottom();
        if (my < lt || my >= lb || mx < left || mx >= left + panelWidth) {
            return false;
        }
        int index;
        if (grid) {
            int col = (int) ((mx - left - 8) / 18);
            int row = (int) ((my - lt + scroll) / 18);
            if (col < 0 || col >= columns()) {
                return false;
            }
            index = row * columns() + col;
        } else {
            index = (int) ((my - lt + scroll) / 14);
        }
        if (index >= 0 && index < filtered.size()) {
            pick(filtered.get(index));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !filtered.isEmpty()) {
            pick(filtered.getFirst());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
