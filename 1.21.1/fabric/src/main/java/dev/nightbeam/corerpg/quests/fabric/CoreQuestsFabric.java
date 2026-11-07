package dev.nightbeam.corerpg.quests.fabric;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.network.Packets;
import dev.nightbeam.corerpg.quests.registry.RegistryHelper;
import dev.nightbeam.corerpg.quests.server.CommonEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Supplier;

public final class CoreQuestsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CoreQuests.init(new RegistryHelper() {
            @Override
            public <R, T extends R> Supplier<T> register(Registry<R> registry, String name, Supplier<T> factory) {
                T value = Registry.register(registry, CoreQuests.id(name), factory.get());
                return () -> value;
            }
        });
        registerPackets();
        ServerLifecycleEvents.SERVER_STARTING.register(CommonEvents::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CommonEvents.serverStopped());
        ServerTickEvents.END_SERVER_TICK.register(CommonEvents::serverTickEnd);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CommonEvents.playerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CommonEvents.playerLeave(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CommonEvents.registerCommands(dispatcher, context));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !CommonEvents.redirectChat(sender, message.signedContent()));
    }

    private static void registerPackets() {
        for (Packets.ToClient<?> packet : Packets.toClient()) {
            registerToClient(packet);
        }
        for (Packets.ToServer<?> packet : Packets.toServer()) {
            registerToServer(packet);
        }
    }

    private static <T extends CustomPacketPayload> void registerToClient(Packets.ToClient<T> packet) {
        PayloadTypeRegistry.playS2C().register(packet.type(), packet.codec());
    }

    private static <T extends CustomPacketPayload> void registerToServer(Packets.ToServer<T> packet) {
        PayloadTypeRegistry.playC2S().register(packet.type(), packet.codec());
        ServerPlayNetworking.registerGlobalReceiver(packet.type(), (payload, context) ->
                context.server().execute(() -> packet.handler().accept(payload, context.player())));
    }
}
