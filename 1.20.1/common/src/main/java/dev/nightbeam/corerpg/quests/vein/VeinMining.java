package dev.nightbeam.corerpg.quests.vein;

import dev.nightbeam.corerpg.quests.config.CoreConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server side of vein mining. Holding the key and breaking a block breaks the connected vein (or the chosen shape)
 * through the normal block-breaking path, so protection mods, tool damage, enchantments and drops all behave as usual.
 */
public final class VeinMining {
    private record State(boolean active, VeinShape shape) {
    }

    private record Pending(ServerPlayer player, BlockPos origin, BlockState originState, List<BlockPos> positions) {
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static boolean breaking;
    private static @Nullable BlockPos gatherAt;

    private VeinMining() {
    }

    public static void reset() {
        STATES.clear();
        COOLDOWNS.clear();
        PENDING.clear();
        breaking = false;
        gatherAt = null;
    }

    public static void onLeave(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    public static void setState(ServerPlayer player, boolean active, int shape) {
        STATES.put(player.getUUID(), new State(active, VeinShape.byIndex(shape)));
    }

    public static boolean isActive(Player player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.active && CoreConfig.SERVER.veinEnabled.get();
    }

    /** Where block drops should land while a vein mine gathers them (read by the drop mixin). */
    public static @Nullable BlockPos gatherPosition() {
        return gatherAt;
    }

    /** Called before a player breaks a block. Plans the vein; it is broken at the end of the tick. */
    public static void onBlockBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        if (breaking || !isActive(player) || player.isSpectator()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (CoreConfig.SERVER.veinRequireTool.get() && !tool.isDamageableItem()) {
            player.displayClientMessage(Component.translatable("corerpg_quests.vein.need_tool").withStyle(ChatFormatting.RED), true);
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long readyAt = COOLDOWNS.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            player.displayClientMessage(Component.translatable("corerpg_quests.vein.cooldown", (readyAt - now + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }
        State st = STATES.get(player.getUUID());
        HitResult hit = player.pick(player.blockInteractionRange() + 1, 1.0F, false);
        Direction face = hit instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos) ? bhr.getDirection() : Direction.UP;
        List<BlockPos> positions = plan(player.level(), player, pos, state, face, st.shape, CoreConfig.SERVER.veinMaxBlocks.get());
        if (positions.size() > 1) {
            PENDING.add(new Pending(player, pos.immutable(), state, positions.subList(1, positions.size())));
        }
    }

    /** The positions a vein mine would cover (origin first). Also used by the client preview. */
    public static List<BlockPos> plan(Level level, Player player, BlockPos origin, BlockState originState, Direction face, VeinShape shape, int max) {
        ItemStack tool = player.getMainHandItem();
        if (!VeinRules.canBreak(level, origin, originState, player, tool, false)) {
            return List.of(origin);
        }
        Predicate<BlockState> same = VeinRules.sameAs(originState);
        return VeinShapes.collect(shape, origin, face, player.getDirection(),
                p -> level.isLoaded(p) && same.test(level.getBlockState(p)) && VeinRules.canBreak(level, p, level.getBlockState(p), player, tool, false),
                p -> level.isLoaded(p) && VeinRules.canBreak(level, p, level.getBlockState(p), player, tool, true),
                max);
    }

    /** Breaks the planned veins. Runs at the end of the server tick. */
    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Pending> work = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending pending : work) {
            execute(pending);
        }
    }

    private static void execute(Pending pending) {
        ServerPlayer player = pending.player;
        ServerLevel level = player.serverLevel();
        if (player.isRemoved() || level.getBlockState(pending.origin).equals(pending.originState)) {
            return; // the original break was cancelled (protected area, event...)
        }
        boolean creative = player.isCreative();
        double exhaustion = CoreConfig.SERVER.veinExhaustionPerBlock.get();
        int xpCost = CoreConfig.SERVER.veinXpPerBlock.get();
        boolean preventBreak = CoreConfig.SERVER.veinPreventToolBreak.get();
        breaking = true;
        gatherAt = CoreConfig.SERVER.veinGatherDrops.get() ? pending.origin : null;
        int broken = 0;
        try {
            for (BlockPos pos : pending.positions) {
                ItemStack tool = player.getMainHandItem();
                if (!creative) {
                    if (preventBreak && tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) {
                        player.displayClientMessage(Component.translatable("corerpg_quests.vein.tool_saved").withStyle(ChatFormatting.YELLOW), true);
                        break;
                    }
                    if (exhaustion > 0 && player.getFoodData().getFoodLevel() <= 0) {
                        player.displayClientMessage(Component.translatable("corerpg_quests.vein.too_hungry").withStyle(ChatFormatting.RED), true);
                        break;
                    }
                    if (xpCost > 0 && player.totalExperience < xpCost) {
                        player.displayClientMessage(Component.translatable("corerpg_quests.vein.no_xp").withStyle(ChatFormatting.RED), true);
                        break;
                    }
                }
                BlockState state = level.getBlockState(pos);
                if (!VeinRules.canBreak(level, pos, state, player, tool, false)) {
                    continue;
                }
                if (!player.gameMode.destroyBlock(pos)) {
                    continue;
                }
                broken++;
                if (!creative) {
                    if (exhaustion > 0) {
                        player.causeFoodExhaustion((float) exhaustion);
                    }
                    if (xpCost > 0) {
                        player.giveExperiencePoints(-xpCost);
                    }
                }
            }
        } finally {
            breaking = false;
            gatherAt = null;
        }
        int cooldown = CoreConfig.SERVER.veinCooldownTicks.get();
        if (broken > 0 && cooldown > 0 && !creative) {
            COOLDOWNS.put(player.getUUID(), level.getGameTime() + cooldown);
        }
    }

    // ---------------------------------------------------------------- Right-click

    /**
     * Called before a player uses an item on a block. Harvests and replants ripe crops, and with the key held applies
     * axes, shovels and hoes to the connected area. Returns true when the interaction was handled.
     */
    public static boolean onUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (breaking || hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = hit.getBlockPos();
        BlockState state = level.getBlockState(origin);
        boolean active = isActive(player);
        if (isRipeCrop(state)) {
            if (active && CoreConfig.SERVER.veinRightClickHarvest.get()) {
                Predicate<BlockState> same = s -> s.is(state.getBlock()) && isRipeCrop(s);
                List<BlockPos> field = VeinShapes.collect(VeinShape.SHAPELESS, origin, hit.getDirection(), player.getDirection(),
                        p -> p.getY() == origin.getY() && same.test(level.getBlockState(p)), p -> false, CoreConfig.SERVER.veinMaxBlocks.get());
                field.forEach(p -> harvest(level, player, p));
                return true;
            }
            if (CoreConfig.SERVER.veinSingleCropHarvest.get() && !player.isShiftKeyDown()) {
                harvest(level, player, origin);
                return true;
            }
            return false;
        }
        if (!active || !CoreConfig.SERVER.veinRightClickTools.get()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof AxeItem || held.getItem() instanceof ShovelItem || held.getItem() instanceof HoeItem)) {
            return false;
        }
        List<BlockPos> area;
        if (held.getItem() instanceof AxeItem) {
            Predicate<BlockState> same = VeinRules.sameAs(state);
            area = VeinShapes.collect(VeinShape.SHAPELESS, origin, hit.getDirection(), player.getDirection(),
                    p -> same.test(level.getBlockState(p)), p -> false, CoreConfig.SERVER.veinMaxBlocks.get());
        } else {
            Block block = state.getBlock();
            area = VeinShapes.collect(VeinShape.SHAPELESS, origin, hit.getDirection(), player.getDirection(),
                    p -> p.getY() == origin.getY() && level.getBlockState(p).is(block) && level.getBlockState(p.above()).isAir(),
                    p -> false, CoreConfig.SERVER.veinMaxBlocks.get());
        }
        if (area.size() <= 1) {
            return false;
        }
        breaking = true;
        try {
            for (BlockPos pos : area.subList(1, area.size())) {
                if (held.isEmpty() || (held.isDamageableItem() && held.getMaxDamage() - held.getDamageValue() <= 1 && !player.isCreative())) {
                    break;
                }
                Vec3 center = Vec3.atCenterOf(pos);
                UseOnContext ctx = new UseOnContext(player, hand, new BlockHitResult(center, hit.getDirection(), pos, false));
                InteractionResult result = held.useOn(ctx);
                if (!result.consumesAction()) {
                    break;
                }
            }
        } finally {
            breaking = false;
        }
        return false; // let vanilla handle the origin block itself
    }

    static boolean isRipeCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        return false;
    }

    /** Drops the crop's loot minus one seed, and puts the crop back at age 0. */
    @SuppressWarnings("deprecation")
    private static void harvest(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isRipeCrop(state)) {
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, player.getMainHandItem());
        ItemStack seed = state.getBlock().getCloneItemStack(level, pos, state);
        boolean replanted = false;
        for (ItemStack drop : drops) {
            if (!replanted && !seed.isEmpty() && ItemStack.isSameItem(drop, seed)) {
                drop.shrink(1);
                replanted = true;
            }
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
        BlockState reset = state.getBlock() instanceof CropBlock crop ? crop.getStateForAge(0)
                : state.getBlock() == Blocks.NETHER_WART ? state.setValue(NetherWartBlock.AGE, 0) : state;
        level.setBlock(pos, reset, Block.UPDATE_ALL);
        level.levelEvent(2001, pos, Block.getId(state));
    }
}
