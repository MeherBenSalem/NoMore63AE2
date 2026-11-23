package dev.nightbeam.corerpg.quests.fabric;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.client.ClientEvents;
import dev.nightbeam.corerpg.quests.client.CoreQuestsClient;
import dev.nightbeam.corerpg.quests.client.KeyBinds;
import dev.nightbeam.corerpg.quests.network.Packets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class CoreQuestsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CoreQuestsClient.init();
        KeyBinds.ALL.forEach(KeyMappingHelper::registerKeyMapping);
        for (Packets.ToClient<?> packet : Packets.toClient()) {
            register(packet);
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientEvents.tick());
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, CoreQuests.id("hud"), ClientEvents::renderHud);
        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
                ClientEvents.submitWorld(context.submitNodeCollector(), context.levelState().cameraRenderState.pos));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvents.disconnect());
    }

    private static <T extends CustomPacketPayload> void register(Packets.ToClient<T> packet) {
        ClientPlayNetworking.registerGlobalReceiver(packet.type(), (payload, context) ->
                context.client().execute(() -> packet.handler().accept(payload)));
    }
}
