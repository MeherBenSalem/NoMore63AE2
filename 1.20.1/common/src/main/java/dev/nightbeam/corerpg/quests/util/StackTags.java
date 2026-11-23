package dev.nightbeam.corerpg.quests.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Minecraft 1.20.1 edition: the mod's item data (filter expression, loot crate table) lives in the stack's NBT. */
public final class StackTags {
    private StackTags() {
    }

    public static String getString(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : null;
    }

    public static void putString(ItemStack stack, String key, String value) {
        stack.getOrCreateTag().putString(key, value);
    }

    public static void remove(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(key);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }
}
