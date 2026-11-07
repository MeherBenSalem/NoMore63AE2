package dev.nightbeam.corerpg.quests;

import dev.nightbeam.corerpg.quests.filter.FilterTypes;
import dev.nightbeam.corerpg.quests.network.Packets;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import dev.nightbeam.corerpg.quests.registry.RegistryHelper;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point shared by every loader. Loader modules call {@link #init(RegistryHelper)} once during mod construction.
 */
public final class CoreQuests {
    public static final String MOD_ID = "corerpg_quests";
    public static final String MOD_NAME = "Core RPG - Quests Engine";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private static boolean initialized;

    private CoreQuests() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init(RegistryHelper registries) {
        if (initialized) {
            return;
        }
        initialized = true;
        TaskTypes.bootstrap();
        RewardTypes.bootstrap();
        FilterTypes.bootstrap();
        ModRegistry.register(registries);
        Packets.bootstrap();
        LOG.info("{} initialised: {} task types, {} reward types, {} filter types", MOD_NAME,
                TaskTypes.all().size(), RewardTypes.all().size(), FilterTypes.all().size());
    }
}
