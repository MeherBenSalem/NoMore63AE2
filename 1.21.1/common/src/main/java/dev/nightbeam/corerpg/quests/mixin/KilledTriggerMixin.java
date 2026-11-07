package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.ServerQuests;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.KilledTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KilledTrigger.class)
public abstract class KilledTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void corerpg$onKill(ServerPlayer player, Entity killed, DamageSource source, CallbackInfo ci) {
        if ((Object) this == CriteriaTriggers.PLAYER_KILLED_ENTITY) {
            ServerQuests sq = ServerQuests.getOrNull();
            if (sq != null) {
                sq.engine().onKill(player, killed);
            }
        }
    }
}
