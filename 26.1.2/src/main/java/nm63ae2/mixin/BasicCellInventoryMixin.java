/*
 * No More 63 AE2
 *
 * Mixin: appeng.me.cells.BasicCellInventory
 *
 * --------------------------------------------------------------------
 * Why this exists
 * --------------------------------------------------------------------
 * AE2's ME item storage cells are hard-capped to 63 unique item types.
 * The cap is enforced in BasicCellInventory's constructor:
 *
 *     private static final int MAX_ITEM_TYPES = 63;
 *     ...
 *     if (this.maxItemTypes > MAX_ITEM_TYPES) {
 *         this.maxItemTypes = MAX_ITEM_TYPES;
 *     }
 *
 * No public config or API hook is provided to change this. This mixin
 * replaces the effective cap for item-type cells with the value configured
 * in this addon's ModConfigSpec (default 4096, range 63..65535).
 *
 * --------------------------------------------------------------------
 * What this mixin changes
 * --------------------------------------------------------------------
 *  * For cells whose AEKeyType is items(), the per-cell type cap is taken
 *    from nm63ae2.NoMore63AE2Config.getMaxItemTypesPerCell() instead of 63.
 *  * Fluid cells and any other non-item basic cells are NOT touched.
 *  * No other AE2 behavior is modified: bytes, amounts, channels, drain,
 *    tooltip rendering, upgrade cards, partition logic, etc. all behave
 *    exactly as vanilla AE2.
 *
 * --------------------------------------------------------------------
 * Why this injection point
 * --------------------------------------------------------------------
 * We inject at the INVOKE of BasicCellInventory.getUpgradesInventory(),
 * which sits *after* AE2's clamp (line 78-83 of BasicCellInventory) and
 * *before* the equal-distribution card math (line 110+) that derives
 * maxItemsPerType from maxItemTypes. This is the exact point where
 * maxItemTypes has been clamped to 63 but has not yet been used to
 * compute per-type capacity, so overriding it here lets the equal-
 * distribution card recompute with the new (larger) cap and stay
 * byte-consistent.
 */
package nm63ae2.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.me.cells.BasicCellInventory;

import nm63ae2.NoMore63AE2Config;

@Mixin(BasicCellInventory.class)
public abstract class BasicCellInventoryMixin {

    @Shadow
    private int maxItemTypes;

    @Shadow
    private AEKeyType keyType;

    @Inject(
            method = "<init>(Lappeng/api/storage/cells/IBasicCellItem;Lnet/minecraft/world/item/ItemStack;Lappeng/api/storage/cells/ISaveProvider;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/cells/BasicCellInventory;getUpgradesInventory()Lappeng/api/upgrades/IUpgradeInventory;",
                    shift = At.Shift.BEFORE,
                    remap = false))
    private void noMore63AE2$overrideMaxItemTypes(
            IBasicCellItem cellType,
            ItemStack stack,
            ISaveProvider container,
            CallbackInfo ci) {
        // Restrict to item storage cells. Fluid cells and any other key types
        // retain AE2's default 63-cap exactly.
        if (keyType == AEKeyType.items()) {
            int configured = NoMore63AE2Config.getMaxItemTypesPerCell();
            // Defensive clamp: spec already enforces the range, but if the spec
            // hasn't been loaded yet we still must not write a dangerous value.
            if (configured < NoMore63AE2Config.MIN_SAFE_VALUE) {
                configured = NoMore63AE2Config.MIN_SAFE_VALUE;
            } else if (configured > NoMore63AE2Config.MAX_SAFE_VALUE) {
                configured = NoMore63AE2Config.MAX_SAFE_VALUE;
            }
            this.maxItemTypes = configured;
        }
    }
}
