package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.task.TaskContext;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.TriggerEvent;
import dev.nightbeam.corerpg.quests.task.TriggerKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.Set;

/** Place blocks (build a camp, light torches, plant saplings). */
public class PlaceBlockTask extends BlockTask {
    @Override
    public TaskType<?> type() {
        return TaskTypes.PLACE_BLOCK;
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(TriggerKind.BLOCK_PLACE);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        if (event instanceof TriggerEvent.BlockPlace place && matches(place.state())) {
            ctx.addProgress(1);
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.place_block.title", value, blockName());
    }

    @Override
    protected Icon defaultIcon() {
        return blockIcon(Icon.of(Items.BRICKS));
    }
}
