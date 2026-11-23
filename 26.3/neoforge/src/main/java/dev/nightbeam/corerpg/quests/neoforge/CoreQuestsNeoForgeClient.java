package dev.nightbeam.corerpg.quests.neoforge;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.client.ClientEvents;
import dev.nightbeam.corerpg.quests.client.CoreQuestsClient;
import dev.nightbeam.corerpg.quests.client.KeyBinds;
import dev.nightbeam.corerpg.quests.network.Packets;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CoreQuests.MOD_ID, dist = Dist.CLIENT)
public final class CoreQuestsNeoForgeClient {
    public CoreQuestsNeoForgeClient(IEventBus modBus) {
        CoreQuestsClient.init();
        modBus.addListener((RegisterKeyMappingsEvent event) -> KeyBinds.ALL.forEach(event::register));
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.CHAT, CoreQuests.id("hud"), ClientEvents::renderHud));
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            for (Packets.ToClient<?> packet : Packets.toClient()) {
                register(event, packet);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientEvents.tick());
        NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent event) ->
                ClientEvents.submitWorld(event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState.pos));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientEvents.disconnect());
    }

    private static <T extends CustomPacketPayload> void register(RegisterClientPayloadHandlersEvent event, Packets.ToClient<T> packet) {
        event.register(packet.type(), (payload, context) -> context.enqueueWork(() -> packet.handler().accept(payload)));
    }
}
