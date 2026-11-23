package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/** Have a team stage (given by another quest's reward, a command, a script or another mod). */
public class StageTask extends Task {
    public String stage = "";

    @Override
    public TaskType<?> type() {
        return TaskTypes.STAGE;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.string("stage", "", () -> stage, v -> stage = v);
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(TriggerKind.STAGE);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        recheck(ctx);
    }

    @Override
    public void recheck(TaskContext ctx) {
        if (!stage.isBlank() && ctx.stages().contains(stage)) {
            ctx.setProgress(1);
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.stage.title", stage);
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.NAME_TAG);
    }
}
