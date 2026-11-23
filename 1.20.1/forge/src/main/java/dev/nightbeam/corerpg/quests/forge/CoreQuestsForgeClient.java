package dev.nightbeam.corerpg.quests.forge;

import dev.nightbeam.corerpg.quests.client.ClientEvents;
import dev.nightbeam.corerpg.quests.client.CoreQuestsClient;
import dev.nightbeam.corerpg.quests.client.KeyBinds;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

final class CoreQuestsForgeClient {
    private CoreQuestsForgeClient() {
    }

    static void init(IEventBus modBus) {
        CoreQuestsClient.init();
        modBus.addListener((RegisterKeyMappingsEvent event) -> KeyBinds.ALL.forEach(event::register));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "hud",
                (gui, graphics, partialTick, screenWidth, screenHeight) -> ClientEvents.renderHud(graphics, partialTick)));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                ClientEvents.tick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                ClientEvents.renderWorld(event.getPoseStack(), event.getCamera().getPosition(), Minecraft.getInstance().renderBuffers().bufferSource());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientEvents.disconnect());
    }
}
