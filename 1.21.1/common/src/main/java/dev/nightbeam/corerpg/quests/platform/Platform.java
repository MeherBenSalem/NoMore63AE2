package dev.nightbeam.corerpg.quests.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;

import java.nio.file.Path;

/** Everything common code needs from the mod loader. Implemented once per loader and found by {@link Services}. */
public interface Platform {
    /** "neoforge" or "fabric". */
    String loaderName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    /** True on the physical client (singleplayer included). */
    boolean isPhysicalClient();

    Path configDir();

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);

    /** Whether the connected peer has this mod's channel, so optional packets are not sent to vanilla clients. */
    boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type);

    CreativeModeTab.Builder creativeTabBuilder();
}
