package dev.nightbeam.corerpg.quests.mixin;

import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import dev.nightbeam.corerpg.quests.server.ServerQuests;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Keeps quest books on death (book setting): they are set aside before the drop and put back right after. */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    @Unique
    private final List<Integer> corerpg$keptSlots = new ArrayList<>();
    @Unique
    private final List<ItemStack> corerpg$kept = new ArrayList<>();

    @Inject(method = "dropAll", at = @At("HEAD"))
    private void corerpg$keepBooks(CallbackInfo ci) {
        corerpg$kept.clear();
        corerpg$keptSlots.clear();
        ServerQuests sq = ServerQuests.getOrNull();
        if (sq == null || !sq.book().keepBookOnDeath || ModRegistry.QUEST_BOOK == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.is(ModRegistry.QUEST_BOOK.get())) {
                corerpg$keptSlots.add(i);
                corerpg$kept.add(stack);
                items.set(i, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "dropAll", at = @At("RETURN"))
    private void corerpg$restoreBooks(CallbackInfo ci) {
        for (int i = 0; i < corerpg$kept.size(); i++) {
            items.set(corerpg$keptSlots.get(i), corerpg$kept.get(i));
        }
        corerpg$kept.clear();
        corerpg$keptSlots.clear();
    }
}
