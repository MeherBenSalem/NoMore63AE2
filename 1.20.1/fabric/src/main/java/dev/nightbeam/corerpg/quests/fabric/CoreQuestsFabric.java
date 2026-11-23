package dev.nightbeam.corerpg.quests.fabric;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.network.Packets;
import dev.nightbeam.corerpg.quests.network.compat.CustomPacketPayload;
import dev.nightbeam.corerpg.quests.registry.RegistryHelper;
import dev.nightbeam.corerpg.quests.server.CommonEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;

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
        for (Packets.ToServer<?> packet : Packets.toServer()) {
            registerToServer(packet);
        }
        ServerLifecycleEvents.SERVER_STARTING.register(CommonEvents::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CommonEvents.serverStopped());
        ServerTickEvents.END_SERVER_TICK.register(CommonEvents::serverTickEnd);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CommonEvents.playerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CommonEvents.playerLeave(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CommonEvents.registerCommands(dispatcher, context));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !CommonEvents.redirectChat(sender, message.signedContent()));
    }

    private static <T extends CustomPacketPayload> void registerToServer(Packets.ToServer<T> packet) {
        // Decode on the network thread (the buffer is released afterwards), handle on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(packet.type().id(), (server, player, handler, buf, sender) -> {
            T payload = packet.codec().decode(buf);
            server.execute(() -> packet.handler().accept(payload, player));
        });
    }
}
