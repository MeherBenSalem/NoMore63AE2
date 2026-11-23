package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.CommonEvents;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import net.minecraft.advancements.critereon.BredAnimalsTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BredAnimalsTrigger.class)
public abstract class BredAnimalsTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void corerpg$onBreed(ServerPlayer player, Animal parent, Animal partner, @Nullable AgeableMob child, CallbackInfo ci) {
        CommonEvents.trigger(player, new TriggerEvent.Breed(child != null ? child : parent, parent));
    }
}
