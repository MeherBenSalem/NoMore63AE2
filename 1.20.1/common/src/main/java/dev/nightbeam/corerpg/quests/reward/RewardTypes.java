package dev.nightbeam.corerpg.quests.reward;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.reward.types.AdvancementReward;
import dev.nightbeam.corerpg.quests.reward.types.CommandReward;
import dev.nightbeam.corerpg.quests.reward.types.CustomReward;
import dev.nightbeam.corerpg.quests.reward.types.ItemReward;
import dev.nightbeam.corerpg.quests.reward.types.LootReward;
import dev.nightbeam.corerpg.quests.reward.types.StageReward;
import dev.nightbeam.corerpg.quests.reward.types.TableReward;
import dev.nightbeam.corerpg.quests.reward.types.ToastReward;
import dev.nightbeam.corerpg.quests.reward.types.UnknownReward;
import dev.nightbeam.corerpg.quests.reward.types.XpReward;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** The reward type registry. */
public final class RewardTypes {
    private static final Map<ResourceLocation, RewardType<?>> TYPES = new LinkedHashMap<>();

    public static final RewardType<ItemReward> ITEM = register("item", ItemReward::new, () -> Icon.of(Items.CHEST));
    public static final RewardType<XpReward> XP = register("xp", XpReward::points, () -> Icon.of(Items.EXPERIENCE_BOTTLE));
    public static final RewardType<XpReward> XP_LEVELS = register("xp_levels", XpReward::levels, () -> Icon.of(Items.EXPERIENCE_BOTTLE));
    public static final RewardType<TableReward> RANDOM = register("random", TableReward::random, () -> Icon.of(Items.CHEST));
    public static final RewardType<TableReward> CHOICE = register("choice", TableReward::choice, () -> Icon.of(Items.ENDER_CHEST));
    public static final RewardType<TableReward> ALL_TABLE = register("all_table", TableReward::all, () -> Icon.of(Items.BARREL));
    public static final RewardType<LootReward> LOOT = register("loot", LootReward::new, () -> Icon.of(Items.CHEST_MINECART));
    public static final RewardType<CommandReward> COMMAND = register("command", CommandReward::new, () -> Icon.of(Items.COMMAND_BLOCK));
    public static final RewardType<AdvancementReward> ADVANCEMENT = register("advancement", AdvancementReward::new, () -> Icon.of(Items.KNOWLEDGE_BOOK));
    public static final RewardType<ToastReward> TOAST = register("toast", ToastReward::new, () -> Icon.of(Items.PAPER));
    public static final RewardType<StageReward> STAGE = register("stage", StageReward::new, () -> Icon.of(Items.NAME_TAG));
    public static final RewardType<CustomReward> CUSTOM = register("custom", CustomReward::new, () -> Icon.of(Items.COMMAND_BLOCK));
    public static final RewardType<UnknownReward> UNKNOWN = register("unknown", UnknownReward::new, () -> Icon.of(Items.BARRIER));

    private RewardTypes() {
    }

    public static void bootstrap() {
    }

    private static <T extends Reward> RewardType<T> register(String path, Supplier<T> factory, Supplier<Icon> icon) {
        return register(CoreQuests.id(path), factory, icon);
    }

    public static synchronized <T extends Reward> RewardType<T> register(ResourceLocation id, Supplier<T> factory, Supplier<Icon> icon) {
        if (TYPES.containsKey(id)) {
            throw new IllegalStateException("Reward type " + id + " is already registered");
        }
        RewardType<T> type = new RewardType<>(id, factory, icon);
        TYPES.put(id, type);
        return type;
    }

    public static @Nullable RewardType<?> byId(String id) {
        ResourceLocation rl = id.contains(":") ? ResourceLocation.tryParse(id) : CoreQuests.id(id);
        return rl == null ? null : TYPES.get(rl);
    }

    public static Collection<RewardType<?>> all() {
        Map<ResourceLocation, RewardType<?>> copy = new LinkedHashMap<>(TYPES);
        copy.remove(UNKNOWN.id());
        return Collections.unmodifiableCollection(copy.values());
    }

    public static Reward read(JsonObject json, JsonCtx ctx) {
        String typeName = json.has("type") ? json.get("type").getAsString() : "";
        RewardType<?> type = byId(typeName);
        Reward reward = type == null || type == UNKNOWN ? UNKNOWN.create() : type.create();
        reward.setIdInternal(json.has("id") ? json.get("id").getAsString() : "");
        reward.fromJson(json, ctx);
        return reward;
    }
}
