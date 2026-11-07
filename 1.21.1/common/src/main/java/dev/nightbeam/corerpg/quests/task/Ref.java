package dev.nightbeam.corerpg.quests.task;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A registry id or a tag ("minecraft:zombie" or "#minecraft:raiders"). */
public record Ref(ResourceLocation id, boolean tag) {
    public static @Nullable Ref parse(String text) {
        String s = text.trim();
        boolean tag = s.startsWith("#");
        if (tag) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(s);
        return id == null ? null : new Ref(id, tag);
    }

    public boolean matches(EntityType<?> type) {
        if (tag) {
            return type.is(TagKey.create(Registries.ENTITY_TYPE, id));
        }
        return EntityType.getKey(type).equals(id);
    }

    public boolean matches(Entity entity) {
        return matches(entity.getType());
    }

    public boolean matches(BlockState state) {
        if (tag) {
            return state.is(TagKey.create(Registries.BLOCK, id));
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
    }

    public <T> boolean matches(Holder<T> holder, ResourceKey<? extends Registry<T>> registry) {
        if (tag) {
            return holder.is(TagKey.create(registry, id));
        }
        return holder.is(id);
    }

    public @Nullable Block block() {
        return tag || !BuiltInRegistries.BLOCK.containsKey(id) ? null : BuiltInRegistries.BLOCK.get(id);
    }

    @Override
    public String toString() {
        return tag ? "#" + id : id.toString();
    }
}
