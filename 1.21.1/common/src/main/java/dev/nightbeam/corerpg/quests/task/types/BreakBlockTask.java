package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.task.ItemMatcher;
import dev.nightbeam.corerpg.quests.task.ComponentMatch;
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

/** Break blocks (mine ore, fell trees), optionally only with tools that match a filter. */
public class BreakBlockTask extends BlockTask {
    public String toolFilter = "";

    @Override
    public TaskType<?> type() {
        return TaskTypes.BREAK_BLOCK;
    }

    @Override
    protected void defineTaskProps(Props p) {
        super.defineTaskProps(p);
        p.filter("tool_filter", () -> toolFilter, v -> toolFilter = v);
    }

    @Override
    public Set<TriggerKind> triggers() {
        return EnumSet.of(TriggerKind.BLOCK_BREAK);
    }

    @Override
    public void onTrigger(TaskContext ctx, TriggerEvent event) {
        if (event instanceof TriggerEvent.BlockBreak br && matches(br.state())
                && (toolFilter.isBlank() || ItemMatcher.matches(ItemStack.EMPTY, toolFilter, ComponentMatch.IGNORE, br.tool()))) {
            ctx.addProgress(1);
        }
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.task.break_block.title", value, blockName());
    }

    @Override
    protected Icon defaultIcon() {
        return blockIcon(Icon.of(Items.IRON_PICKAXE));
    }
}
