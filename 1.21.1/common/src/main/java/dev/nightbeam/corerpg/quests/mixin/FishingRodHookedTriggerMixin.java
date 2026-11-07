package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.CommonEvents;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import net.minecraft.advancements.critereon.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(FishingRodHookedTrigger.class)
public abstract class FishingRodHookedTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void corerpg$onFish(ServerPlayer player, ItemStack rod, FishingHook hook, Collection<ItemStack> items, CallbackInfo ci) {
        List<ItemStack> copy = new ArrayList<>(items.size());
        items.forEach(s -> copy.add(s.copy()));
        CommonEvents.trigger(player, new TriggerEvent.Fish(copy));
    }
}
