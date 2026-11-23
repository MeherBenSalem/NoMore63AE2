package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.CommonEvents;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import net.minecraft.advancements.critereon.TradeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TradeTrigger.class)
public abstract class TradeTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void corerpg$onTrade(ServerPlayer player, AbstractVillager merchant, ItemStack result, CallbackInfo ci) {
        CommonEvents.trigger(player, new TriggerEvent.Trade(result.copy(), merchant));
    }
}
