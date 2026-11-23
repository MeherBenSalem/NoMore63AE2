package dev.nightbeam.corerpg.quests.item;

import dev.nightbeam.corerpg.quests.ClientBridge;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.RewardTable;
import dev.nightbeam.corerpg.quests.registry.ModRegistry;
import dev.nightbeam.corerpg.quests.server.ServerQuests;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** A crate made from a reward table: right-click to roll the table. Mobs can drop crates (see the table settings). */
public class LootCrateItem extends Item {
    public LootCrateItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(RewardTable table) {
        ItemStack stack = new ItemStack(ModRegistry.LOOT_CRATE.get());
        stack.set(ModRegistry.LOOT_CRATE_COMPONENT.get(), table.id());
        return stack;
    }

    public static @Nullable RewardTable table(ItemStack stack, @Nullable QuestBook book) {
        String id = stack.get(ModRegistry.LOOT_CRATE_COMPONENT.get());
        return id == null || book == null ? null : book.table(id);
    }

    public static void addCrates(Consumer<ItemStack> output) {
        QuestBook book = ClientBridge.anyBook();
        if (book == null) {
            return;
        }
        for (RewardTable table : book.tables) {
            if (table.lootCrate) {
                output.accept(of(table));
            }
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ServerQuests quests = ServerQuests.getOrNull();
        RewardTable table = quests == null ? null : table(stack, quests.book());
        if (table == null) {
            player.displayClientMessage(Component.translatable("item.corerpg_quests.loot_crate.missing").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        int count = player.isShiftKeyDown() ? stack.getCount() : 1;
        for (int i = 0; i < count; i++) {
            quests.engine().openCrate(serverPlayer, table);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN.value(), SoundSource.PLAYERS, 0.6F, 1.1F);
        stack.consume(count, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        RewardTable table = table(stack, ClientBridge.anyBook());
        if (table == null) {
            return super.getName(stack);
        }
        return table.crateDisplayName().copy().withStyle(s -> s.withColor(TextColor.fromRgb(table.crateColor)));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        RewardTable table = table(stack, ClientBridge.anyBook());
        return table != null && table.crateGlow;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("item.corerpg_quests.loot_crate.tooltip").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("item.corerpg_quests.loot_crate.tooltip_shift").withStyle(ChatFormatting.DARK_GRAY));
    }
}
