package dev.nightbeam.corerpg.quests.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Builds the id lists the editor suggests from. Some registries (structures, loot tables, advancements) only exist on
 * the server, so they are sent when a player enters editing mode.
 */
final class EditorDataBuilder {
    private EditorDataBuilder() {
    }

    static JsonObject build(MinecraftServer server) {
        JsonObject json = new JsonObject();
        TreeSet<String> advancements = new TreeSet<>();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (!holder.id().getPath().startsWith("recipes/")) {
                advancements.add(holder.id().toString());
            }
        }
        json.add("advancement", array(advancements));
        json.add("structure", array(keys(server.registryAccess().registryOrThrow(Registries.STRUCTURE))));
        json.add("structure_tag", array(tags(server.registryAccess().registryOrThrow(Registries.STRUCTURE).getTagNames().map(t -> t.location()))));
        json.add("biome", array(keys(server.registryAccess().registryOrThrow(Registries.BIOME))));
        json.add("biome_tag", array(tags(server.registryAccess().registryOrThrow(Registries.BIOME).getTagNames().map(t -> t.location()))));
        TreeSet<String> loot = new TreeSet<>();
        server.reloadableRegistries().getKeys(Registries.LOOT_TABLE).forEach(id -> loot.add(id.toString()));
        json.add("loot_table", array(loot));
        TreeSet<String> dims = new TreeSet<>();
        for (ResourceKey<Level> key : server.levelKeys()) {
            dims.add(key.location().toString());
        }
        json.add("dimension", array(dims));
        json.add("stat", array(keys(BuiltInRegistries.CUSTOM_STAT)));
        json.add("item_tag", array(tags(BuiltInRegistries.ITEM.getTagNames().map(t -> t.location()))));
        json.add("block_tag", array(tags(BuiltInRegistries.BLOCK.getTagNames().map(t -> t.location()))));
        json.add("entity_tag", array(tags(BuiltInRegistries.ENTITY_TYPE.getTagNames().map(t -> t.location()))));
        TreeSet<String> stages = new TreeSet<>();
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq != null) {
            sq.teams().all().forEach(t -> stages.addAll(t.stages()));
        }
        json.add("stage", array(stages));
        return json;
    }

    private static TreeSet<String> keys(Registry<?> registry) {
        TreeSet<String> set = new TreeSet<>();
        for (ResourceLocation id : registry.keySet()) {
            set.add(id.toString());
        }
        return set;
    }

    private static TreeSet<String> tags(Stream<ResourceLocation> ids) {
        TreeSet<String> set = new TreeSet<>();
        ids.forEach(id -> set.add(id.toString()));
        return set;
    }

    private static JsonArray array(Collection<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
