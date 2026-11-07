package dev.nightbeam.corerpg.quests.registry;

import com.mojang.serialization.Codec;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.item.LootCrateItem;
import dev.nightbeam.corerpg.quests.item.MissingItem;
import dev.nightbeam.corerpg.quests.item.QuestBookItem;
import dev.nightbeam.corerpg.quests.item.SmartFilterItem;
import dev.nightbeam.corerpg.quests.platform.Services;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.function.Supplier;

/** Every registered game object. Fields stay null until {@link #register} runs (unit tests never call it). */
public final class ModRegistry {
    public static Supplier<DataComponentType<String>> FILTER_COMPONENT;
    public static Supplier<DataComponentType<String>> LOOT_CRATE_COMPONENT;

    public static Supplier<Item> QUEST_BOOK;
    public static Supplier<Item> SMART_FILTER;
    public static Supplier<Item> LOOT_CRATE;
    public static Supplier<Item> MISSING_ITEM;

    public static Supplier<CreativeModeTab> TAB;

    private ModRegistry() {
    }

    public static void register(RegistryHelper helper) {
        FILTER_COMPONENT = helper.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "filter", () ->
                DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
        LOOT_CRATE_COMPONENT = helper.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "loot_crate", () ->
                DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

        QUEST_BOOK = helper.register(BuiltInRegistries.ITEM, "quest_book", () -> new QuestBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
        SMART_FILTER = helper.register(BuiltInRegistries.ITEM, "smart_filter", () -> new SmartFilterItem(new Item.Properties().stacksTo(16)));
        LOOT_CRATE = helper.register(BuiltInRegistries.ITEM, "loot_crate", () -> new LootCrateItem(new Item.Properties()));
        MISSING_ITEM = helper.register(BuiltInRegistries.ITEM, "missing_item", () -> new MissingItem(new Item.Properties()));

        TAB = helper.register(BuiltInRegistries.CREATIVE_MODE_TAB, "main", () -> Services.PLATFORM.creativeTabBuilder()
                .title(Component.translatable("itemGroup." + CoreQuests.MOD_ID))
                .icon(() -> new ItemStack(QUEST_BOOK.get()))
                .displayItems((params, output) -> {
                    output.accept(QUEST_BOOK.get());
                    output.accept(SMART_FILTER.get());
                    LootCrateItem.addCrates(output);
                })
                .build());
    }
}
