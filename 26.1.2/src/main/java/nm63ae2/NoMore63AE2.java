/*
 * No More 63 AE2
 * Copyright (c) 2026
 *
 * Licensed under the GNU Lesser General Public License v3 (LGPLv3) for
 * consistency with the parent Applied Energistics 2 project.
 *
 * Owns the addon-wide ModConfigSpec. The value is consumed by
 * BasicCellInventoryMixin to override AE2's hardcoded MAX_ITEM_TYPES cap.
 */
package nm63ae2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * No More 63 AE2 main mod class.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Register the addon's {@link ModConfigSpec} (COMMON) with NeoForge.</li>
 *   <li>Log the active configured item-type limit during startup.</li>
 *   <li>Reload the cached value when the config is (re)loaded.</li>
 * </ol>
 */
@Mod(NoMore63AE2.MOD_ID)
public class NoMore63AE2 {

    public static final String MOD_ID = "no_more_63_ae2";
    public static final String DISPLAY_NAME = "No More 63 AE2";

    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private static final NoMore63AE2Config CONFIG = new NoMore63AE2Config();

    public NoMore63AE2(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CONFIG.spec());

        // Subscribe on the mod bus so we hear (re)loads of our own COMMON config.
        modBus.register(ConfigReloader.class);

        LOG.info("[{}] {} loaded (default maxItemTypesPerCell = {}, range {}..{}).",
                MOD_ID, DISPLAY_NAME, CONFIG.DEFAULT_VALUE, CONFIG.MIN_SAFE_VALUE, CONFIG.MAX_SAFE_VALUE);
    }

    public static NoMore63AE2Config config() {
        return CONFIG;
    }

    /**
     * Tiny helper that re-reads the spec whenever the config is loaded or reloaded
     * and logs the new active value. The mixin reads via {@link NoMore63AE2Config#getMaxItemTypesPerCell()}
     * which always defensively clamps, so reload safety is for cosmetics/logging.
     */
    private static final class ConfigReloader {
        @net.neoforged.bus.api.SubscribeEvent
        static void onConfigLoaded(ModConfigEvent.Loading event) {
            if (event.getConfig().getType() == ModConfig.Type.COMMON
                    && MOD_ID.equals(event.getConfig().getModId())) {
                LOG.info("[{}] config loaded. maxItemTypesPerCell = {}",
                        MOD_ID, CONFIG.maxItemTypesPerCell());
            }
        }

        @net.neoforged.bus.api.SubscribeEvent
        static void onConfigReloaded(ModConfigEvent.Reloading event) {
            if (event.getConfig().getType() == ModConfig.Type.COMMON
                    && MOD_ID.equals(event.getConfig().getModId())) {
                LOG.info("[{}] config reloaded. maxItemTypesPerCell = {}",
                        MOD_ID, CONFIG.maxItemTypesPerCell());
            }
        }
    }
}
