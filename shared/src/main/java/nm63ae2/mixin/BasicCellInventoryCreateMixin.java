package nm63ae2.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.me.cells.BasicCellInventory;

import nm63ae2.CellTypeLimitPatches;

/**
 * Patches newly created AE2 cell inventories after construction.
 * Avoids constructor {@code @Inject} callbacks, which are unreliable on Forge 1.20.1 (SRG).
 */
@Mixin(value = BasicCellInventory.class, remap = false)
public abstract class BasicCellInventoryCreateMixin {

    @Inject(
            method = "createInventory(Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)Lappeng/me/cells/BasicCellInventory;",
            at = @At("RETURN"),
            remap = false)
    private static void noMore63AE2$patchCreatedCell(CallbackInfoReturnable<BasicCellInventory> cir) {
        CellTypeLimitPatches.applyAfterCreate(cir.getReturnValue());
    }
}
