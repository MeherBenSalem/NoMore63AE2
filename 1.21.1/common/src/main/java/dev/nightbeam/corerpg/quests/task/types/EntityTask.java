package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.EnumSet;
import java.util.Set;

/** Breed or tame entities of a type or tag. */
public class EntityTask extends CountTask {
    private final TaskType<?> type;
    private final TriggerKind trigger;
    private final Item fallbackIcon;
    public Ref entity = null;

    private EntityTask(TaskType<?> type, TriggerKind trigger, Item fallbackIcon) {
        this.type = type;
        this.trigger = trigger;
        this.fallbackIcon = fallbackIcon;
    }

    public static EntityTask breed() {
        return new EntityTask(TaskTypes.BREED, TriggerKind.BREED, Items.WHEAT);
    }

    public static EntityTask tame() {
        return new EntityTask(TaskTypes.TAME, TriggerKind.TAME, Items.BONE);
    }

    @Override
    public TaskType<?> type() {
        return type;
    }

    @Override
    protected void defineTaskProps(Props p) {
        p.ref("entity", ResourceKind.ENTITY, () -> entity, v -> entity = v);
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(trigger);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        Entity subject = switch (event) {
            case TriggerEvent.Breed breed -> breed.parent();
            case TriggerEvent.Tame tame -> tame.animal();
            default -> null;
        };
        if (subject != null && (entity == null || entity.matches(subject))) {
            ctx.addProgress(1);
        }
    }

    @Override
    protected Component defaultTitle() {
        Component what = entity == null ? Component.translatable("corerpg_quests.any_entity")
                : entity.tag() ? Component.literal("#" + entity.id())
                : BuiltInRegistries.ENTITY_TYPE.getOptional(entity.id()).map(EntityType::getDescription).orElse(Component.literal(entity.id().toString()));
        return Component.translatable("corerpg_quests.task." + type.id().getPath() + ".title", value, what);
    }

    @Override
    protected Icon defaultIcon() {
        if (entity != null && !entity.tag()) {
            SpawnEggItem egg = SpawnEggItem.byId(BuiltInRegistries.ENTITY_TYPE.get(entity.id()));
            if (egg != null) {
                return Icon.of(egg);
            }
        }
        return Icon.of(fallbackIcon);
    }
}
