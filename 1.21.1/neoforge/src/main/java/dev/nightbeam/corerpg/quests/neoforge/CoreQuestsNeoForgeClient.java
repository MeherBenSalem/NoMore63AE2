package dev.nightbeam.corerpg.quests.neoforge;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.client.ClientEvents;
import dev.nightbeam.corerpg.quests.client.CoreQuestsClient;
import dev.nightbeam.corerpg.quests.client.KeyBinds;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

final class CoreQuestsNeoForgeClient {
    private CoreQuestsNeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        CoreQuestsClient.init();
        modBus.addListener((RegisterKeyMappingsEvent event) -> KeyBinds.ALL.forEach(event::register));
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.CHAT, CoreQuests.id("hud"), ClientEvents::renderHud));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientEvents.tick());
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                ClientEvents.renderWorld(event.getPoseStack(), event.getCamera().getPosition(), Minecraft.getInstance().renderBuffers().bufferSource());
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientEvents.disconnect());
    }
}
