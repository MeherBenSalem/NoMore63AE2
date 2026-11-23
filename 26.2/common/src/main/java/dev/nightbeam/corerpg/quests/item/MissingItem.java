package dev.nightbeam.corerpg.quests.item;

import dev.nightbeam.corerpg.quests.util.ItemJson;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/** Stands in for an item from a mod that is no longer installed, so the quest book keeps it. */
public class MissingItem extends Item {
    public MissingItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = ItemJson.missingId(stack);
        return Component.translatable("item.corerpg_quests.missing_item.named", id == null || id.isEmpty() ? "?" : id).withStyle(ChatFormatting.RED);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("item.corerpg_quests.missing_item.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
