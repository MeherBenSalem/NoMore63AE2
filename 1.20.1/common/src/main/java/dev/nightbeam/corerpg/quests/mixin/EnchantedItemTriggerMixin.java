package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.CommonEvents;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import net.minecraft.advancements.critereon.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantedItemTrigger.class)
public abstract class EnchantedItemTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void corerpg$onEnchant(ServerPlayer player, ItemStack stack, int levels, CallbackInfo ci) {
        CommonEvents.trigger(player, new TriggerEvent.Enchant(stack.copy(), levels));
    }
}
