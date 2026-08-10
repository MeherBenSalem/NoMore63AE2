package nm63ae2.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.stacks.AEKeyType;
import appeng.me.cells.BasicCellInventory;

@Mixin(value = BasicCellInventory.class, remap = false)
public interface BasicCellInventoryAccessor {

    @Accessor("maxItemTypes")
    void nm63$setMaxItemTypes(int value);

    @Mutable
    @Accessor("maxItemsPerType")
    void nm63$setMaxItemsPerType(long value);

    @Accessor("keyType")
    AEKeyType nm63$getKeyType();
}
