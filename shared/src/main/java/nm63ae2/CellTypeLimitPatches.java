package nm63ae2;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEKeyType;
import appeng.core.definitions.AEItems;
import appeng.me.cells.BasicCellInventory;

import nm63ae2.mixin.accessor.BasicCellInventoryAccessor;

public final class CellTypeLimitPatches {

    private CellTypeLimitPatches() {
    }

    public static void applyAfterCreate(BasicCellInventory inventory) {
        if (inventory == null) {
            return;
        }

        var accessor = (BasicCellInventoryAccessor) inventory;
        if (accessor.nm63$getKeyType() != AEKeyType.items()) {
            return;
        }

        int configured = clamp(NoMore63AE2Config.getMaxItemTypesPerCell());
        accessor.nm63$setMaxItemTypes(configured);
        recomputeEqualDistribution(inventory, accessor, configured);
    }

    private static void recomputeEqualDistribution(
            BasicCellInventory inventory,
            BasicCellInventoryAccessor accessor,
            int maxTypes) {
        var upgrades = inventory.getUpgradesInventory();
        if (!upgrades.isInstalled(AEItems.EQUAL_DISTRIBUTION_CARD)) {
            return;
        }

        var config = inventory.getConfigInventory();
        boolean isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
        IncludeExclude partitionListMode = inventory.getPartitionListMode();

        long boundedTypes = Integer.MAX_VALUE;
        if (!isFuzzy && partitionListMode == IncludeExclude.WHITELIST && !config.keySet().isEmpty()) {
            boundedTypes = config.keySet().size();
        }
        boundedTypes = Math.min(boundedTypes, maxTypes);

        long totalStorage = (inventory.getTotalBytes() - (long) inventory.getBytesPerType() * boundedTypes)
                * accessor.nm63$getKeyType().getAmountPerByte();
        accessor.nm63$setMaxItemsPerType(Math.max(0, (totalStorage + boundedTypes - 1) / boundedTypes));
    }

    private static int clamp(int value) {
        if (value < NoMore63AE2Config.MIN_SAFE_VALUE) {
            return NoMore63AE2Config.MIN_SAFE_VALUE;
        }
        if (value > NoMore63AE2Config.MAX_SAFE_VALUE) {
            return NoMore63AE2Config.MAX_SAFE_VALUE;
        }
        return value;
    }
}
