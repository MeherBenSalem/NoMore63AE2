package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/** Earn an advancement, or one criterion of it. Works with datapack advancements, so it can track nearly anything. */
public class AdvancementTask extends Task {
    public ResourceLocation advancement = ResourceLocation.withDefaultNamespace("story/root");
    public String criterion = "";

    @Override
    public TaskType<?> type() {
        return TaskTypes.ADVANCEMENT;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.resource("advancement", ResourceKind.ADVANCEMENT, ResourceLocation.withDefaultNamespace("story/root"), () -> advancement, v -> advancement = v);
        p.string("criterion", "", () -> criterion, v -> criterion = v);
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(TriggerKind.ADVANCEMENT);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        if (event instanceof TriggerEvent.Advancement adv && adv.id().equals(advancement)) {
            recheck(ctx);
        }
    }

    @Override
    public void recheck(TaskContext ctx) {
        if (isDone(ctx.player())) {
            ctx.setProgress(1);
        }
    }

    public boolean isDone(ServerPlayer player) {
        AdvancementHolder holder = player.server.getAdvancements().get(advancement);
        if (holder == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (criterion.isBlank()) {
            return progress.isDone();
        }
        CriterionProgress c = progress.getCriterion(criterion);
        return c != null && c.isDone();
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.advancement.title", Component.literal(advancement.toString()));
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.KNOWLEDGE_BOOK);
    }
}
