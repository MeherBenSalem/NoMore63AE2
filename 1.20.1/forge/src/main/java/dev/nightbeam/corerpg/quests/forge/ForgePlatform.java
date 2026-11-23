package dev.nightbeam.corerpg.quests.forge;

import dev.nightbeam.corerpg.quests.network.compat.CustomPacketPayload;
import dev.nightbeam.corerpg.quests.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Path;

public final class ForgePlatform implements Platform {
    @Override
    public String loaderName() {
        return "forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public boolean isPhysicalClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        CoreQuestsForge.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CoreQuestsForge.Envelope(payload));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        CoreQuestsForge.CHANNEL.sendToServer(new CoreQuestsForge.Envelope(payload));
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return CoreQuestsForge.CHANNEL.isRemotePresent(player.connection.connection);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }
}
