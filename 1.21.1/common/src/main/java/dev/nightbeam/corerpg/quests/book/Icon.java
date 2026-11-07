package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.util.ItemJson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * What a quest object shows as its icon: an item (with components, so enchanted glint and custom models work) or a
 * texture. JSON: an item id string, an item object, or {"texture": "namespace:textures/path.png"}.
 */
public final class Icon {
    public static final Icon EMPTY = new Icon(ItemStack.EMPTY, null);

    private final ItemStack stack;
    private final @Nullable ResourceLocation texture;

    private Icon(ItemStack stack, @Nullable ResourceLocation texture) {
        this.stack = stack;
        this.texture = texture;
    }

    public static Icon of(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : new Icon(stack.copyWithCount(1), null);
    }

    public static Icon of(ItemLike item) {
        return of(new ItemStack(item));
    }

    public static Icon texture(ResourceLocation texture) {
        return new Icon(ItemStack.EMPTY, texture);
    }

    public boolean isEmpty() {
        return stack.isEmpty() && texture == null;
    }

    public ItemStack stack() {
        return stack;
    }

    public @Nullable ResourceLocation texture() {
        return texture;
    }

    public Icon copy() {
        return isEmpty() ? EMPTY : new Icon(stack.copy(), texture);
    }

    public JsonElement toJson(JsonCtx ctx) {
        if (texture != null) {
            JsonObject o = new JsonObject();
            o.addProperty("texture", texture.toString());
            return o;
        }
        return stack.isEmpty() ? new JsonPrimitive("") : ItemJson.write(stack, ctx);
    }

    public static Icon fromJson(JsonElement json, JsonCtx ctx) {
        if (json.isJsonPrimitive() && json.getAsString().isBlank()) {
            return EMPTY;
        }
        if (json.isJsonObject() && json.getAsJsonObject().has("texture")) {
            ResourceLocation id = ResourceLocation.tryParse(json.getAsJsonObject().get("texture").getAsString());
            if (id == null) {
                throw new IllegalArgumentException("Invalid texture " + json);
            }
            return texture(id);
        }
        return of(ItemJson.read(json, ctx));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Icon other)) {
            return false;
        }
        return ItemStack.matches(stack, other.stack) && java.util.Objects.equals(texture, other.texture);
    }

    @Override
    public int hashCode() {
        return texture != null ? texture.hashCode() : ItemStack.hashItemAndComponents(stack);
    }
}
