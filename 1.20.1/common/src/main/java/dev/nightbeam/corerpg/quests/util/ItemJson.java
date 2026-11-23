package dev.nightbeam.corerpg.quests.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item stacks in quest files. A stack with only an item is written as its id ("minecraft:stone"); anything else uses
 * the vanilla item stack format ({"id", "count", "components"}). Items from mods that are no longer installed become a
 * placeholder that remembers the original JSON, so saving the book never loses them.
 */
public final class ItemJson {
    private static final String ORIGINAL = "corerpg_quests:original";

    private ItemJson() {
    }

    public static JsonElement write(ItemStack stack, JsonCtx ctx) {
        if (stack.isEmpty()) {
            return new JsonPrimitive("minecraft:air");
        }
        if (ModRegistry.MISSING_ITEM != null && stack.is(ModRegistry.MISSING_ITEM.get())) {
            CompoundTag data = stack.getTag();
            if (data != null && data.contains(ORIGINAL)) {
                return JsonParser.parseString(data.getString(ORIGINAL));
            }
        }
        if (stack.getCount() == 1 && !stack.hasTag()) {
            return new JsonPrimitive(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        var result = ItemStack.CODEC.encodeStart(ctx.ops(), stack);
        return result.result().orElseThrow(() -> new IllegalArgumentException("Cannot save item " + stack + ": "
                + result.error().map(e -> e.message()).orElse("unknown error")));
    }

    public static ItemStack read(JsonElement json, JsonCtx ctx) {
        String idText = json.isJsonPrimitive() ? json.getAsString() : json.getAsJsonObject().has("id") ? json.getAsJsonObject().get("id").getAsString() : "";
        ResourceLocation id = ResourceLocation.tryParse(idText.trim());
        if (id == null) {
            throw new IllegalArgumentException("Invalid item id '" + idText + "'");
        }
        if (id.equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) {
            return ItemStack.EMPTY;
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return missing(json, id);
        }
        if (json.isJsonPrimitive()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            return new ItemStack(item);
        }
        JsonObject object = json.getAsJsonObject();
        return ItemStack.CODEC.parse(ctx.ops(), object).resultOrPartial(error ->
                CoreQuests.LOG.warn("Item {}: {}", object, error)).orElseGet(() -> new ItemStack(BuiltInRegistries.ITEM.get(id)));
    }

    private static ItemStack missing(JsonElement json, ResourceLocation id) {
        CoreQuests.LOG.warn("Unknown item {} in the quest book (is its mod missing?); keeping it as a placeholder", id);
        if (ModRegistry.MISSING_ITEM == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ModRegistry.MISSING_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(ORIGINAL, json.toString());
        tag.putString("corerpg_quests:missing_id", id.toString());
        stack.setTag(tag);
        return stack;
    }

    /** The id a missing-item placeholder stands in for, or null. */
    public static String missingId(ItemStack stack) {
        CompoundTag data = stack.getTag();
        return data == null || !data.contains("corerpg_quests:missing_id") ? null : data.getString("corerpg_quests:missing_id");
    }
}
