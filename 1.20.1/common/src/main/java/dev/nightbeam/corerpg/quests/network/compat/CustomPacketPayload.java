package dev.nightbeam.corerpg.quests.network.compat;

import net.minecraft.resources.ResourceLocation;

/**
 * Minecraft 1.20.1 edition: the packet shape of 1.20.5+ ({@code CustomPacketPayload}) rebuilt on plain
 * {@link net.minecraft.network.FriendlyByteBuf} channels, so the packet list is the same on every version.
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
