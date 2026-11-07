package dev.nightbeam.corerpg.quests.registry;

import net.minecraft.core.Registry;

import java.util.function.Supplier;

/**
 * Registers game objects in a loader-neutral way. NeoForge defers registration to its register event,
 * Fabric registers immediately; either way the returned supplier is valid once registries are frozen.
 */
public interface RegistryHelper {
    <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory);
}
