package dev.nightbeam.corerpg.quests.task;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.task.types.AdvancementTask;
import dev.nightbeam.corerpg.quests.task.types.BiomeTask;
import dev.nightbeam.corerpg.quests.task.types.BreakBlockTask;
import dev.nightbeam.corerpg.quests.task.types.CheckmarkTask;
import dev.nightbeam.corerpg.quests.task.types.CustomTask;
import dev.nightbeam.corerpg.quests.task.types.DimensionTask;
import dev.nightbeam.corerpg.quests.task.types.EntityTask;
import dev.nightbeam.corerpg.quests.task.types.FluidTask;
import dev.nightbeam.corerpg.quests.task.types.InteractionTask;
import dev.nightbeam.corerpg.quests.task.types.ItemEventTask;
import dev.nightbeam.corerpg.quests.task.types.ItemTask;
import dev.nightbeam.corerpg.quests.task.types.KillTask;
import dev.nightbeam.corerpg.quests.task.types.LocationTask;
import dev.nightbeam.corerpg.quests.task.types.ObservationTask;
import dev.nightbeam.corerpg.quests.task.types.PlaceBlockTask;
import dev.nightbeam.corerpg.quests.task.types.StageTask;
import dev.nightbeam.corerpg.quests.task.types.StatTask;
import dev.nightbeam.corerpg.quests.task.types.StructureTask;
import dev.nightbeam.corerpg.quests.task.types.UnknownTask;
import dev.nightbeam.corerpg.quests.task.types.XpTask;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** The task type registry. Built-in types are registered at class load; addons call {@link #register}. */
public final class TaskTypes {
    private static final Map<ResourceLocation, TaskType<?>> TYPES = new LinkedHashMap<>();

    public static final TaskType<ItemTask> ITEM = register("item", ItemTask::new, () -> Icon.of(Items.CHEST));
    public static final TaskType<CheckmarkTask> CHECKMARK = register("checkmark", CheckmarkTask::new, () -> Icon.texture(CoreQuests.id("textures/gui/icon/check.png")));
    public static final TaskType<KillTask> KILL = register("kill", KillTask::new, () -> Icon.of(Items.IRON_SWORD));
    public static final TaskType<AdvancementTask> ADVANCEMENT = register("advancement", AdvancementTask::new, () -> Icon.of(Items.KNOWLEDGE_BOOK));
    public static final TaskType<LocationTask> LOCATION = register("location", LocationTask::new, () -> Icon.of(Items.COMPASS));
    public static final TaskType<DimensionTask> DIMENSION = register("dimension", DimensionTask::new, () -> Icon.of(Items.OBSIDIAN));
    public static final TaskType<BiomeTask> BIOME = register("biome", BiomeTask::new, () -> Icon.of(Items.OAK_SAPLING));
    public static final TaskType<StructureTask> STRUCTURE = register("structure", StructureTask::new, () -> Icon.of(Items.FILLED_MAP));
    public static final TaskType<StatTask> STAT = register("stat", StatTask::new, () -> Icon.of(Items.PAPER));
    public static final TaskType<XpTask> XP = register("xp", XpTask::new, () -> Icon.of(Items.EXPERIENCE_BOTTLE));
    public static final TaskType<ObservationTask> OBSERVATION = register("observation", ObservationTask::new, () -> Icon.of(Items.SPYGLASS));
    public static final TaskType<InteractionTask> INTERACTION = register("interaction", InteractionTask::new, () -> Icon.of(Items.LEVER));
    public static final TaskType<StageTask> STAGE = register("stage", StageTask::new, () -> Icon.of(Items.NAME_TAG));
    public static final TaskType<CustomTask> CUSTOM = register("custom", CustomTask::new, () -> Icon.of(Items.COMMAND_BLOCK));
    public static final TaskType<FluidTask> FLUID = register("fluid", FluidTask::new, () -> Icon.of(Items.WATER_BUCKET));
    public static final TaskType<BreakBlockTask> BREAK_BLOCK = register("break_block", BreakBlockTask::new, () -> Icon.of(Items.IRON_PICKAXE));
    public static final TaskType<PlaceBlockTask> PLACE_BLOCK = register("place_block", PlaceBlockTask::new, () -> Icon.of(Items.BRICKS));
    public static final TaskType<ItemEventTask> CRAFT = register("craft", ItemEventTask::craft, () -> Icon.of(Items.CRAFTING_TABLE));
    public static final TaskType<ItemEventTask> CONSUME = register("consume", ItemEventTask::consume, () -> Icon.of(Items.APPLE));
    public static final TaskType<ItemEventTask> FISH = register("fish", ItemEventTask::fish, () -> Icon.of(Items.FISHING_ROD));
    public static final TaskType<ItemEventTask> TRADE = register("trade", ItemEventTask::trade, () -> Icon.of(Items.EMERALD));
    public static final TaskType<ItemEventTask> ENCHANT = register("enchant", ItemEventTask::enchant, () -> Icon.of(Items.ENCHANTING_TABLE));
    public static final TaskType<EntityTask> BREED = register("breed", EntityTask::breed, () -> Icon.of(Items.WHEAT));
    public static final TaskType<EntityTask> TAME = register("tame", EntityTask::tame, () -> Icon.of(Items.BONE));
    public static final TaskType<UnknownTask> UNKNOWN = register("unknown", UnknownTask::new, () -> Icon.of(Items.BARRIER));

    private TaskTypes() {
    }

    /** Forces class loading so the built-in types exist. */
    public static void bootstrap() {
    }

    private static <T extends Task> TaskType<T> register(String path, Supplier<T> factory, Supplier<Icon> icon) {
        return register(CoreQuests.id(path), factory, icon);
    }

    public static synchronized <T extends Task> TaskType<T> register(ResourceLocation id, Supplier<T> factory, Supplier<Icon> icon) {
        if (TYPES.containsKey(id)) {
            throw new IllegalStateException("Task type " + id + " is already registered");
        }
        TaskType<T> type = new TaskType<>(id, factory, icon);
        TYPES.put(id, type);
        return type;
    }

    public static @Nullable TaskType<?> byId(String id) {
        ResourceLocation rl = id.contains(":") ? ResourceLocation.tryParse(id) : CoreQuests.id(id);
        return rl == null ? null : TYPES.get(rl);
    }

    /** Every type an author can pick (without the placeholder for missing types). */
    public static Collection<TaskType<?>> all() {
        Map<ResourceLocation, TaskType<?>> copy = new LinkedHashMap<>(TYPES);
        copy.remove(UNKNOWN.id());
        return Collections.unmodifiableCollection(copy.values());
    }

    /** Reads a task; tasks of unknown types are kept as {@link UnknownTask}. */
    public static Task read(JsonObject json, JsonCtx ctx) {
        String typeName = json.has("type") ? json.get("type").getAsString() : "";
        TaskType<?> type = byId(typeName);
        Task task = type == null || type == UNKNOWN ? UNKNOWN.create() : type.create();
        task.setIdInternal(json.has("id") ? json.get("id").getAsString() : "");
        task.fromJson(json, ctx);
        return task;
    }
}
