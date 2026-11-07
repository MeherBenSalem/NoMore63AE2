package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.CommonEvents;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crafting: called for results taken from crafting tables, furnaces, smithing tables and similar slots. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void corerpg$onCrafted(Level level, Player player, int amount, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack self = (ItemStack) (Object) this;
            CommonEvents.trigger(serverPlayer, new TriggerEvent.Craft(self.copy(), Math.max(amount, 1)));
        }
    }
}
