package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Reach (or pay) an amount of experience, in levels or points. */
public class XpTask extends Task {
    public long value = 5;
    public boolean points = false;
    public boolean consume = true;

    @Override
    public TaskType<?> type() {
        return TaskTypes.XP;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.category("task");
        p.longValue("value", 5, 1, Integer.MAX_VALUE, () -> value, v -> value = v);
        p.bool("points", false, () -> points, v -> points = v);
        p.bool("consume", true, () -> consume, v -> consume = v);
    }

    @Override
    public long maxProgress() {
        return value;
    }

    @Override
    public boolean polls() {
        return !consume;
    }

    @Override
    public void poll(TaskContext ctx) {
        long have = points ? ctx.player().totalExperience : ctx.player().experienceLevel;
        if (have > ctx.progress()) {
            ctx.setProgress(Math.min(have, value));
        }
    }

    @Override
    public boolean canSubmit() {
        return consume;
    }

    @Override
    public void submit(TaskContext ctx) {
        ServerPlayer player = ctx.player();
        long needed = value - ctx.progress();
        if (needed <= 0) {
            return;
        }
        if (points) {
            int take = (int) Math.min(needed, player.totalExperience);
            if (take > 0) {
                player.giveExperiencePoints(-take);
                ctx.addProgress(take);
            }
        } else {
            int take = (int) Math.min(needed, player.experienceLevel);
            if (take > 0) {
                player.giveExperienceLevels(-take);
                ctx.addProgress(take);
            }
        }
    }

    @Override
    public Component submitLabel() {
        return Component.translatable("corerpg_quests.task.pay");
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable(points ? "corerpg_quests.task.xp.points" : "corerpg_quests.task.xp.levels", value);
    }

    @Override
    protected Icon defaultIcon() {
        return Icon.of(Items.EXPERIENCE_BOTTLE);
    }
}
