package dev.nightbeam.corerpg.quests.item;

import dev.nightbeam.corerpg.quests.ClientBridge;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Holds a filter expression. Use it to open the filter editor; put it in an item task (or anywhere that takes an item)
 * to accept every item the filter matches.
 */
public class SmartFilterItem extends Item {
    public SmartFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            ClientBridge.openFilterEditor(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        String filter = stack.get(ModRegistry.FILTER_COMPONENT.get());
        return filter != null && !filter.isBlank();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String filter = stack.get(ModRegistry.FILTER_COMPONENT.get());
        if (filter == null || filter.isBlank()) {
            lines.add(Component.translatable("item.corerpg_quests.smart_filter.empty").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.corerpg_quests.smart_filter.filter").withStyle(ChatFormatting.GRAY));
            String shown = filter.length() > 120 ? filter.substring(0, 117) + "..." : filter;
            lines.add(Component.literal(shown).withStyle(ChatFormatting.YELLOW));
        }
        lines.add(Component.translatable("item.corerpg_quests.smart_filter.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
