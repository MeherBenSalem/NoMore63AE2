package dev.nightbeam.corerpg.quests.book;

import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** A decorative (optionally clickable) image on a chapter canvas: banners, arrows, maps, region art. */
public final class ChapterImage extends QuestObject {
    Chapter chapter;
    public ResourceLocation image = ResourceLocation.withDefaultNamespace("textures/item/map.png");
    public double x;
    public double y;
    public double width = 1;
    public double height = 1;
    public double rotation = 0;
    public int color = 0xFFFFFF;
    public int alpha = 255;
    public int order = 0;
    public List<String> hover = new ArrayList<>();
    public ClickAction clickAction = ClickAction.NONE;
    public String clickData = "";
    public boolean editorOnly = false;
    public boolean alignToCorner = false;

    public ChapterImage(Chapter chapter) {
        this.chapter = chapter;
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.IMAGE;
    }

    @Override
    public QuestBook book() {
        return chapter == null ? null : chapter.book();
    }

    public Chapter chapter() {
        return chapter;
    }

    @Override
    protected Component defaultTitle() {
        return Component.literal(image.getPath());
    }

    @Override
    protected void defineProps(Props p) {
        p.category("general");
        p.resource("image", ResourceKind.TEXTURE, ResourceLocation.withDefaultNamespace("textures/item/map.png"), () -> image, v -> image = v);
        p.text("hover", () -> hover, v -> hover = v);
        p.category("appearance");
        p.decimal("x", 0, -100000, 100000, () -> x, v -> x = v);
        p.decimal("y", 0, -100000, 100000, () -> y, v -> y = v);
        p.decimal("width", 1, 0.0625, 1000, () -> width, v -> width = v);
        p.decimal("height", 1, 0.0625, 1000, () -> height, v -> height = v);
        p.decimal("rotation", 0, -360, 360, () -> rotation, v -> rotation = v);
        p.color("color", 0xFFFFFF, () -> color, v -> color = v);
        p.integer("alpha", 255, 0, 255, () -> alpha, v -> alpha = v);
        p.integer("order", 0, -100, 100, () -> order, v -> order = v);
        p.bool("align_to_corner", false, () -> alignToCorner, v -> alignToCorner = v);
        p.category("misc");
        p.enumValue("click_action", ClickAction.class, ClickAction.NONE, () -> clickAction, v -> clickAction = v);
        p.string("click_data", "", () -> clickData, v -> clickData = v);
        p.bool("editor_only", false, () -> editorOnly, v -> editorOnly = v);
    }
}
