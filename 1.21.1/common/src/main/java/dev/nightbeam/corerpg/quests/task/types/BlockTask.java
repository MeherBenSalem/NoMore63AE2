package dev.nightbeam.corerpg.quests.task.types;

import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.schema.Props;
import dev.nightbeam.corerpg.quests.schema.ResourceKind;
import dev.nightbeam.corerpg.quests.task.Ref;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Base for tasks about a block or block tag. */
public abstract class BlockTask extends CountTask {
    public Ref block = null;

    @Override
    protected void defineTaskProps(Props p) {
        p.ref("block", ResourceKind.BLOCK, () -> block, v -> block = v);
    }

    public boolean matches(BlockState state) {
        return block == null || block.matches(state);
    }

    protected Component blockName() {
        if (block == null) {
            return Component.translatable("corerpg_quests.any_block");
        }
        Block b = block.block();
        return b != null ? b.getName() : Component.literal(block.toString());
    }

    protected Icon blockIcon(Icon fallback) {
        Block b = block == null ? null : block.block();
        if (b != null && b.asItem() != net.minecraft.world.item.Items.AIR) {
            return Icon.of(b.asItem());
        }
        return fallback;
    }
}
