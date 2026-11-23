package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.network.chat.Component;

/** Ticked off by hand in the quest book: "I read this", "I found the camp". */
public class CheckmarkTask extends Task {
    @Override
    public TaskType<?> type() {
        return TaskTypes.CHECKMARK;
    }

    @Override
    public boolean canSubmit() {
        return true;
    }

    @Override
    public void submit(TaskContext ctx) {
        ctx.setProgress(1);
    }

    @Override
    public Component submitLabel() {
        return Component.translatable("corerpg_quests.task.check");
    }
}
