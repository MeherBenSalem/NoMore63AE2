package dev.nightbeam.corerpg.quests.client;

import dev.nightbeam.corerpg.quests.client.hud.QuestTracker;
import dev.nightbeam.corerpg.quests.client.screen.QuestBookScreen;
import dev.nightbeam.corerpg.quests.client.screen.TeamScreen;
import dev.nightbeam.corerpg.quests.client.vein.VeinClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/** Client event entry points the loader modules forward to. */
public final class ClientEvents {
    private ClientEvents() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ClientQa.tick(mc);
            return;
        }
        while (KeyBinds.OPEN_BOOK.consumeClick()) {
            if (mc.gui.screen() == null) {
                QuestBookScreen.open("");
            }
        }
        while (KeyBinds.TEAM.consumeClick()) {
            if (mc.gui.screen() == null) {
                mc.gui.setScreen(new TeamScreen(null));
            }
        }
        while (KeyBinds.CYCLE_TRACKER.consumeClick()) {
            QuestTracker.cycle();
        }
        VeinClient.tick(mc);
        ClientQa.tick(mc);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.player == null) {
            return;
        }
        QuestTracker.render(graphics, mc);
        VeinClient.renderOverlay(graphics, mc);
    }

    public static void submitWorld(SubmitNodeCollector collector, Vec3 camera) {
        VeinClient.submitWorld(collector, camera);
    }

    public static void disconnect() {
        ClientQuests.reset();
        VeinClient.reset();
    }
}
