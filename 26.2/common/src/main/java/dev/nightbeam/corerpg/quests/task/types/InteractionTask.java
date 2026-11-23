package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.ComponentMatch;
import dev.nightbeam.corerpg.quests.task.ItemMatcher;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/** Right-click a block or an entity (talk to an NPC, ring the bell, pray at the altar), optionally holding an item. */
public class InteractionTask extends CountTask {
    public ObservationTask.Target target = ObservationTask.Target.ENTITY;
    public Ref block = null;
    public Ref entity = null;
    public String heldFilter = "";

    @Override
    public TaskType<?> type() {
        return TaskTypes.INTERACTION;
    }

    @Override
    protected void defineTaskProps(Props p) {
        p.enumValue("target", ObservationTask.Target.class, ObservationTask.Target.ENTITY, () -> target, v -> target = v);
        p.ref("block", ResourceKind.BLOCK, () -> block, v -> block = v);
        p.ref("entity", ResourceKind.ENTITY, () -> entity, v -> entity = v);
        p.filter("held_filter", () -> heldFilter, v -> heldFilter = v);
    }

    @Override
    public Set<TriggerKind> triggers() {
        return target == ObservationTask.Target.BLOCK ? EnumSet.of(TriggerKind.INTERACT_BLOCK) : EnumSet.of(TriggerKind.INTERACT_ENTITY);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        switch (event) {
            case TriggerEvent.InteractBlock ib -> {
                if ((block == null || block.matches(ib.state())) && held(ib.held())) {
                    ctx.addProgress(1);
                }
            }
            case TriggerEvent.InteractEntity ie -> {
                if ((entity == null || entity.matches(ie.entity())) && held(ie.held())) {
                    ctx.addProgress(1);
                }
            }
            default -> {
            }
        }
    }

    private boolean held(ItemStack stack) {
        return heldFilter.isBlank() || ItemMatcher.matches(ItemStack.EMPTY, heldFilter, ComponentMatch.IGNORE, stack);
    }

    @Override
    protected Component defaultTitle() {
        String what = target == ObservationTask.Target.BLOCK ? (block == null ? "?" : block.toString()) : (entity == null ? "?" : entity.toString());
        return Component.translatable("corerpg_quests.task.interaction.title", Component.literal(what));
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(target == ObservationTask.Target.BLOCK ? Items.LEVER : Items.EMERALD);
    }
}
