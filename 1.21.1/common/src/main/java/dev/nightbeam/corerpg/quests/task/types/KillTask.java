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
import dev.nightbeam.corerpg.quests.util.TextUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/** Kill entities of a type or tag, optionally only ones with a given custom name (named bosses). */
public class KillTask extends CountTask {
    public Ref entity = new Ref(net.minecraft.resources.ResourceLocation.withDefaultNamespace("zombie"), false);
    public String customName = "";

    @Override
    public TaskType<?> type() {
        return TaskTypes.KILL;
    }

    @Override
    protected void defineTaskProps(Props p) {
        p.ref("entity", ResourceKind.ENTITY, () -> entity, v -> entity = v);
        p.string("custom_name", "", () -> customName, v -> customName = v);
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(TriggerKind.KILL);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        if (event instanceof TriggerEvent.Kill kill && matches(kill.entity())) {
            ctx.addProgress(1);
        }
    }

    public boolean matches(Entity target) {
        if (entity != null && !entity.matches(target)) {
            return false;
        }
        if (!customName.isBlank()) {
            return target.hasCustomName() && TextUtil.plain(target.getCustomName().getString()).equals(TextUtil.plain(customName));
        }
        return true;
    }

    @Override
    protected Component defaultTitle() {
        Component what;
        if (!customName.isBlank()) {
            what = TextUtil.format(customName);
        } else if (entity == null) {
            what = Component.translatable("corerpg_quests.any_entity");
        } else if (entity.tag()) {
            what = Component.literal("#" + entity.id());
        } else {
            what = BuiltInRegistries.ENTITY_TYPE.getOptional(entity.id()).map(EntityType::getDescription)
                    .orElse(Component.literal(entity.id().toString()));
        }
        return Component.translatable("corerpg_quests.task.kill.title", value, what);
    }

    @Override
    protected Icon defaultIcon() {
        if (entity != null && !entity.tag()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entity.id());
            SpawnEggItem egg = SpawnEggItem.byId(type);
            if (egg != null) {
                return Icon.of(egg);
            }
        }
        return Icon.of(Items.IRON_SWORD);
    }
}
