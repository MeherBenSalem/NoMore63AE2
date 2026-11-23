package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.server.CommonEvents;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.vein.VeinMining;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block breaking (quests and vein mining) and right-clicking blocks (quests, harvesting, area tools). */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    protected ServerLevel level;

    @Unique
    private BlockState corerpg$breaking;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void corerpg$beforeDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = level.getBlockState(pos);
        corerpg$breaking = state;
        if (!state.isAir()) {
            VeinMining.onBlockBreak(player, pos, state);
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void corerpg$afterDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = corerpg$breaking;
        corerpg$breaking = null;
        if (cir.getReturnValueZ() && state != null && !state.isAir()) {
            CommonEvents.trigger(player, new TriggerEvent.BlockBreak(state, pos.immutable(), player.getMainHandItem().copy()));
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void corerpg$useItemOn(ServerPlayer user, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND) {
            CommonEvents.trigger(user, new TriggerEvent.InteractBlock(world.getBlockState(hit.getBlockPos()), hit.getBlockPos().immutable(), stack.copy()));
        }
        if (VeinMining.onUseBlock(user, hand, hit)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
