package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.ServerQuests;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Saves quest data with the world (autosave, /save-all and shutdown). */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "saveAllChunks", at = @At("HEAD"))
    private void corerpg$save(boolean suppressLog, boolean flush, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq != null) {
            sq.save();
        }
    }
}
