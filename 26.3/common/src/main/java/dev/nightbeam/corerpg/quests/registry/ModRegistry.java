package dev.nightbeam.corerpg.quests.registry;

import com.mojang.serialization.Codec;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.item.LootCrateItem;
import dev.nightbeam.corerpg.quests.item.MissingItem;
import dev.nightbeam.corerpg.quests.item.QuestBookItem;
import dev.nightbeam.corerpg.quests.item.SmartFilterItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Every registered game object. Fields stay null until {@link #register} runs (unit tests never call it). */
public final class ModRegistry {
    public static Supplier<DataComponentType<String>> FILTER_COMPONENT;
    public static Supplier<DataComponentType<String>> LOOT_CRATE_COMPONENT;

    public static Supplier<Item> QUEST_BOOK;
    public static Supplier<Item> SMART_FILTER;
    public static Supplier<Item> LOOT_CRATE;
    public static Supplier<Item> MISSING_ITEM;

    private ModRegistry() {
    }

    public static void register(RegistryHelper helper) {
        FILTER_COMPONENT = helper.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "filter", () ->
                DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
        LOOT_CRATE_COMPONENT = helper.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "loot_crate", () ->
                DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

        QUEST_BOOK = helper.register(BuiltInRegistries.ITEM, "quest_book", () -> new QuestBookItem(properties("quest_book").stacksTo(1).rarity(Rarity.UNCOMMON)));
        SMART_FILTER = helper.register(BuiltInRegistries.ITEM, "smart_filter", () -> new SmartFilterItem(properties("smart_filter").stacksTo(16)));
        LOOT_CRATE = helper.register(BuiltInRegistries.ITEM, "loot_crate", () -> new LootCrateItem(properties("loot_crate")));
        MISSING_ITEM = helper.register(BuiltInRegistries.ITEM, "missing_item", () -> new MissingItem(properties("missing_item")));
        // The creative tab is registered by each loader module (its builder is not reachable from common code here).
    }

    private static Item.Properties properties(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, CoreQuests.id(name)));
    }

    public static Identifier tabId() {
        return CoreQuests.id("main");
    }

    public static Component tabTitle() {
        return Component.translatable("itemGroup." + CoreQuests.MOD_ID);
    }

    public static ItemStack tabIcon() {
        return new ItemStack(QUEST_BOOK.get());
    }

    /** The creative tab contents: the book, the filter and a crate for each loot-crate table of the client's book. */
    public static void forEachTabItem(Consumer<ItemStack> output) {
        output.accept(new ItemStack(QUEST_BOOK.get()));
        output.accept(new ItemStack(SMART_FILTER.get()));
        LootCrateItem.addCrates(output);
    }
}
