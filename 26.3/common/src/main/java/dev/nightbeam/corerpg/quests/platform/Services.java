package dev.nightbeam.corerpg.quests.platform;

import dev.nightbeam.corerpg.quests.CoreQuests;

import java.util.ServiceLoader;

/** Loads the loader-specific implementation of a common interface through {@link ServiceLoader}. */
public final class Services {
    public static final Platform PLATFORM = load(Platform.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        T loaded = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + clazz.getName() + " found"));
        CoreQuests.LOG.debug("Loaded {} for service {}", loaded, clazz);
        return loaded;
    }
}
