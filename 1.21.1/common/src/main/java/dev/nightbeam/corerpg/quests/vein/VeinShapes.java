package dev.nightbeam.corerpg.quests.vein;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure shape algorithms: given the block that was hit, which positions a vein mine covers. They only see the world
 * through predicates, so the client preview, the server and the unit tests all use exactly the same code.
 */
public final class VeinShapes {
    private VeinShapes() {
    }

    /**
     * @param origin    the block being broken (always first in the result)
     * @param face      the face of the origin the player hit
     * @param horizontal the horizontal direction the player is facing (for the escape tunnel)
     * @param matches   for shapeless mining: whether a position holds a block of the same kind as the origin
     * @param breakable for shaped mining: whether a position holds a block that may be broken
     * @param max       the most positions to return (origin included)
     */
    public static List<BlockPos> collect(VeinShape shape, BlockPos origin, Direction face, Direction horizontal,
                                         Predicate<BlockPos> matches, Predicate<BlockPos> breakable, int max) {
        if (max <= 0) {
            return List.of();
        }
        return switch (shape) {
            case SHAPELESS -> shapeless(origin, matches, max);
            case SMALL_TUNNEL -> tunnel(origin, face.getOpposite(), 0, 1, breakable, max);
            case MINING_TUNNEL -> tunnel(origin, face.getOpposite(), 0, 2, breakable, max);
            case LARGE_TUNNEL -> tunnel(origin, face.getOpposite(), 1, 3, breakable, max);
            case SMALL_SQUARE -> square(origin, face, breakable, max);
            case ESCAPE_TUNNEL -> escape(origin, horizontal, breakable, max);
        };
    }

    /** Breadth-first over all 26 neighbours, nearest first, so a capped vein grows evenly from the origin. */
    static List<BlockPos> shapeless(BlockPos origin, Predicate<BlockPos> matches, int max) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty() && result.size() < max) {
            BlockPos pos = queue.poll();
            result.add(pos);
            List<BlockPos> next = new ArrayList<>(26);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos n = pos.offset(dx, dy, dz);
                        if (seen.add(n) && matches.test(n)) {
                            next.add(n);
                        }
                    }
                }
            }
            next.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
            queue.addAll(next);
        }
        return result;
    }

    /**
     * A tunnel along {@code dir}. The cross-section is {@code (2*halfWidth+1)} wide and {@code height} tall, starting at
     * the origin's row (for 3-tall it is centred on the origin). Air is skipped without counting; the tunnel ends after
     * {@code max} breakable blocks or {@code max} layers.
     */
    static List<BlockPos> tunnel(BlockPos origin, Direction dir, int halfWidth, int height, Predicate<BlockPos> breakable, int max) {
        Direction side;
        Direction up;
        if (dir.getAxis() == Direction.Axis.Y) {
            side = Direction.EAST;
            up = Direction.SOUTH;
        } else {
            side = dir.getClockWise();
            up = Direction.UP;
        }
        int bottom = height == 3 ? -1 : 0;
        int top = height == 3 ? 1 : height - 1;
        if (dir.getAxis() == Direction.Axis.Y && height == 2) {
            top = 0;
        }
        Set<BlockPos> result = new LinkedHashSet<>();
        result.add(origin);
        for (int depth = 0; depth < max && result.size() < max; depth++) {
            BlockPos layer = origin.relative(dir, depth);
            for (int h = bottom; h <= top && result.size() < max; h++) {
                for (int w = -halfWidth; w <= halfWidth && result.size() < max; w++) {
                    BlockPos pos = layer.relative(up, h).relative(side, w);
                    if (!pos.equals(origin) && breakable.test(pos)) {
                        result.add(pos);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    static List<BlockPos> square(BlockPos origin, Direction face, Predicate<BlockPos> breakable, int max) {
        Direction a;
        Direction b;
        switch (face.getAxis()) {
            case X -> {
                a = Direction.UP;
                b = Direction.SOUTH;
            }
            case Y -> {
                a = Direction.EAST;
                b = Direction.SOUTH;
            }
            default -> {
                a = Direction.UP;
                b = Direction.EAST;
            }
        }
        List<BlockPos> result = new ArrayList<>();
        result.add(origin);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos pos = origin.relative(a, i).relative(b, j);
                if (result.size() < max && !pos.equals(origin) && breakable.test(pos)) {
                    result.add(pos);
                }
            }
        }
        return result;
    }

    /** A 1-wide, 2-tall staircase: each step one block forward and one block up. */
    static List<BlockPos> escape(BlockPos origin, Direction horizontal, Predicate<BlockPos> breakable, int max) {
        Set<BlockPos> result = new LinkedHashSet<>();
        result.add(origin);
        BlockPos step = origin;
        for (int i = 0; i < max * 2 && result.size() < max; i++) {
            for (int h = 0; h <= 2 && result.size() < max; h++) {
                BlockPos pos = step.above(h);
                if (!pos.equals(origin) && breakable.test(pos)) {
                    result.add(pos);
                }
            }
            step = step.relative(horizontal).above();
        }
        return new ArrayList<>(result);
    }
}
