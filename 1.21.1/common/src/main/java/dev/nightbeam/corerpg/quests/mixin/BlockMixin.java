package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.vein.VeinMining;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** While vein mining gathers drops, items and experience appear at the first block instead of every block. */
@Mixin(Block.class)
public abstract class BlockMixin {
    @ModifyVariable(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), argsOnly = true)
    private static BlockPos corerpg$gatherDrops(BlockPos pos) {
        BlockPos gather = VeinMining.gatherPosition();
        return gather != null ? gather : pos;
    }

    @ModifyVariable(method = "popExperience", at = @At("HEAD"), argsOnly = true)
    private BlockPos corerpg$gatherXp(BlockPos pos) {
        BlockPos gather = VeinMining.gatherPosition();
        return gather != null ? gather : pos;
    }
}
