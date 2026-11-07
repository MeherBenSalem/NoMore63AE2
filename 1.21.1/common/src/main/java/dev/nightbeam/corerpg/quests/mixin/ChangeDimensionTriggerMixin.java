package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.CommonEvents;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import net.minecraft.advancements.critereon.ChangeDimensionTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChangeDimensionTrigger.class)
public abstract class ChangeDimensionTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void corerpg$onDimension(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to, CallbackInfo ci) {
        CommonEvents.trigger(player, new TriggerEvent.Dimension(to));
    }
}
