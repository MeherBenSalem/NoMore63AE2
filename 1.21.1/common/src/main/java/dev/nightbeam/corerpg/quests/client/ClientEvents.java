package dev.nightbeam.corerpg.quests.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightbeam.corerpg.quests.client.hud.QuestTracker;
import dev.nightbeam.corerpg.quests.client.screen.QuestBookScreen;
import dev.nightbeam.corerpg.quests.client.screen.TeamScreen;
import dev.nightbeam.corerpg.quests.client.vein.VeinClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
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
            if (mc.screen == null) {
                QuestBookScreen.open("");
            }
        }
        while (KeyBinds.TEAM.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new TeamScreen(null));
            }
        }
        while (KeyBinds.CYCLE_TRACKER.consumeClick()) {
            QuestTracker.cycle();
        }
        VeinClient.tick(mc);
        ClientQa.tick(mc);
    }

    public static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        QuestTracker.render(graphics, mc);
        VeinClient.renderOverlay(graphics, mc);
    }

    public static void renderWorld(PoseStack poseStack, Vec3 camera, MultiBufferSource.BufferSource buffers) {
        VeinClient.renderWorld(poseStack, camera, buffers);
    }

    public static void disconnect() {
        ClientQuests.reset();
        VeinClient.reset();
    }
}
