package dev.nightbeam.corerpg.quests.vein;

import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Which blocks count as "the same" and which may be broken. Used by both the server and the client preview. */
public final class VeinRules {
    public static final TagKey<Block> BLACKLIST = TagKey.create(Registries.BLOCK, CoreQuests.id("vein_mining_blacklist"));

    private VeinRules() {
    }

    /** Merge tags of the origin block: tags matching a configured pattern ("c:ores/*" matches "c:ores/iron"). */
    public static List<TagKey<Block>> mergeTags(BlockState origin) {
        List<String> patterns = CoreConfig.SERVER.veinMergeTags.get();
        List<TagKey<Block>> result = new ArrayList<>();
        origin.getTags().forEach(tag -> {
            String id = tag.location().toString();
            for (String pattern : patterns) {
                if (matchesPattern(id, pattern)) {
                    result.add(tag);
                    break;
                }
            }
        });
        return result;
    }

    static boolean matchesPattern(String tagId, String pattern) {
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return tagId.startsWith(prefix) || tagId.equals(pattern.substring(0, pattern.length() - 2));
        }
        return tagId.equals(pattern);
    }

    /** "Same kind as the origin": the same block, or sharing one of the origin's merge tags. */
    public static Predicate<BlockState> sameAs(BlockState origin) {
        Block block = origin.getBlock();
        List<TagKey<Block>> tags = mergeTags(origin);
        return state -> {
            if (state.is(block)) {
                return true;
            }
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Whether this block may be broken by vein mining with the given player and tool. */
    public static boolean canBreak(BlockGetter level, BlockPos pos, BlockState state, Player player, ItemStack tool, boolean shaped) {
        if (state.isAir() || state.is(BLACKLIST) || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) {
            return false;
        }
        if (shaped && state.hasBlockEntity()) {
            return false;
        }
        if (!player.isCreative() && CoreConfig.SERVER.veinRequireCorrectTool.get() && state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            return false;
        }
        return true;
    }
}
