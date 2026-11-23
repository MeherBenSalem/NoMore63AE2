package dev.nightbeam.corerpg.quests.schema;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import org.jetbrains.annotations.Nullable;

/**
 * Context for reading and writing quest files: registry access for item stacks and components.
 * {@code registries} may be null in pure unit tests that never touch item stacks.
 */
public final class JsonCtx {
    private final @Nullable HolderLookup.Provider registries;
    private @Nullable DynamicOps<JsonElement> ops;

    public JsonCtx(@Nullable HolderLookup.Provider registries) {
        this.registries = registries;
    }

    public @Nullable HolderLookup.Provider registries() {
        return registries;
    }

    public DynamicOps<JsonElement> ops() {
        if (ops == null) {
            ops = registries == null ? JsonOps.INSTANCE : RegistryOps.create(JsonOps.INSTANCE, registries);
        }
        return ops;
    }
}
