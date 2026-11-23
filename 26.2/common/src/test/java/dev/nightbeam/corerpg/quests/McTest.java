package dev.nightbeam.corerpg.quests;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

/** Base for tests that need vanilla registries (items, blocks, components). */
public abstract class McTest {
    private static boolean componentsBound;

    @BeforeAll
    static synchronized void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (!componentsBound) {
            // Since 26.1 item components are bound with the datapack registries; ItemStack needs them.
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                    .forEach(DataComponentInitializers.PendingComponents::apply);
            componentsBound = true;
        }
    }
}
