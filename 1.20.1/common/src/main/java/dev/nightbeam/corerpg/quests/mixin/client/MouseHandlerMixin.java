package dev.nightbeam.corerpg.quests.mixin.client;

import dev.nightbeam.corerpg.quests.client.vein.VeinClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling while holding the vein mining key changes the shape instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void corerpg$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (window == minecraft.getWindow().getWindow() && minecraft.screen == null && VeinClient.onScroll(yOffset)) {
            ci.cancel();
        }
    }
}
