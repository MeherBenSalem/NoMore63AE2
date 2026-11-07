package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries kept quest books over to the respawned player. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void corerpg$restoreBooks(ServerPlayer old, boolean keepEverything, CallbackInfo ci) {
        if (keepEverything || ModRegistry.QUEST_BOOK == null) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        for (ItemStack stack : old.getInventory().items) {
            if (stack.is(ModRegistry.QUEST_BOOK.get())) {
                self.getInventory().add(stack.copy());
            }
        }
    }
}
