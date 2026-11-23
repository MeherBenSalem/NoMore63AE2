package dev.nightbeam.corerpg.quests.item;

import dev.nightbeam.corerpg.quests.ClientBridge;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * Holds a filter expression. Use it to open the filter editor; put it in an item task (or anywhere that takes an item)
 * to accept every item the filter matches.
 */
public class SmartFilterItem extends Item {
    public SmartFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientBridge.openFilterEditor(hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        String filter = stack.get(ModRegistry.FILTER_COMPONENT.get());
        return filter != null && !filter.isBlank();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        String filter = stack.get(ModRegistry.FILTER_COMPONENT.get());
        if (filter == null || filter.isBlank()) {
            lines.accept(Component.translatable("item.corerpg_quests.smart_filter.empty").withStyle(ChatFormatting.GRAY));
        } else {
            lines.accept(Component.translatable("item.corerpg_quests.smart_filter.filter").withStyle(ChatFormatting.GRAY));
            String shown = filter.length() > 120 ? filter.substring(0, 117) + "..." : filter;
            lines.accept(Component.literal(shown).withStyle(ChatFormatting.YELLOW));
        }
        lines.accept(Component.translatable("item.corerpg_quests.smart_filter.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
