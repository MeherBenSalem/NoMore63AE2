package dev.nightbeam.corerpg.quests.vein;

import dev.nightbeam.corerpg.quests.McTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinShapesTest extends McTest {
    private static final BlockPos O = new BlockPos(10, 64, 10);
    private static final Predicate<BlockPos> ALL = p -> true;
    private static final Predicate<BlockPos> NONE = p -> false;

    @Test
    void shapelessFollowsTheVeinNearestFirst() {
        // A straight vein of 10 blocks along +X plus one diagonal neighbour.
        Set<BlockPos> vein = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            vein.add(O.offset(i, 0, 0));
        }
        vein.add(O.offset(1, 1, 1));
        List<BlockPos> result = VeinShapes.collect(VeinShape.SHAPELESS, O, Direction.UP, Direction.NORTH, vein::contains, NONE, 100);
        assertEquals(11, result.size());
        assertEquals(O, result.getFirst());
        assertEquals(vein, new HashSet<>(result));
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i).distManhattan(O) >= result.get(i - 1).distManhattan(O) - 2, "roughly nearest first");
        }
    }

    @Test
    void shapelessRespectsCapAndGrowsEvenly() {
        List<BlockPos> result = VeinShapes.collect(VeinShape.SHAPELESS, O, Direction.UP, Direction.NORTH, ALL, NONE, 27);
        assertEquals(27, result.size());
        // The 27 nearest positions of a solid world are exactly the 3x3x3 cube.
        for (BlockPos p : result) {
            assertTrue(Math.abs(p.getX() - O.getX()) <= 1 && Math.abs(p.getY() - O.getY()) <= 1 && Math.abs(p.getZ() - O.getZ()) <= 1, p.toString());
        }
        assertEquals(1, VeinShapes.collect(VeinShape.SHAPELESS, O, Direction.UP, Direction.NORTH, ALL, NONE, 1).size());
        assertTrue(VeinShapes.collect(VeinShape.SHAPELESS, O, Direction.UP, Direction.NORTH, ALL, NONE, 0).isEmpty());
    }

    @Test
    void shapelessDoesNotJumpGaps() {
        Set<BlockPos> vein = Set.of(O, O.offset(1, 0, 0), O.offset(3, 0, 0));
        List<BlockPos> result = VeinShapes.collect(VeinShape.SHAPELESS, O, Direction.UP, Direction.NORTH, vein::contains, NONE, 10);
        assertEquals(2, result.size());
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void smallTunnelGoesStraightIntoTheFace(Direction face) {
        List<BlockPos> result = VeinShapes.collect(VeinShape.SMALL_TUNNEL, O, face, Direction.NORTH, NONE, ALL, 8);
        assertEquals(8, result.size());
        Direction into = face.getOpposite();
        for (int i = 0; i < 8; i++) {
            assertEquals(O.relative(into, i), result.get(i));
        }
    }

    @Test
    void tunnelsSkipAirWithoutCounting() {
        Predicate<BlockPos> solidEveryOther = p -> (p.getZ() - O.getZ()) % 2 == 0;
        // Facing south means hitting the north face; the tunnel goes south (+Z).
        List<BlockPos> result = VeinShapes.collect(VeinShape.SMALL_TUNNEL, O, Direction.NORTH, Direction.SOUTH, NONE, solidEveryOther, 7);
        assertEquals(List.of(O, O.south(2), O.south(4), O.south(6)), result);
        // The tunnel is also at most `max` layers deep, so a cave cannot stretch it forever.
        assertEquals(List.of(O, O.south(2)), VeinShapes.collect(VeinShape.SMALL_TUNNEL, O, Direction.NORTH, Direction.SOUTH, NONE, solidEveryOther, 4));
    }

    @Test
    void miningTunnelIsTwoTallWhenHorizontal() {
        List<BlockPos> result = VeinShapes.collect(VeinShape.MINING_TUNNEL, O, Direction.WEST, Direction.EAST, NONE, ALL, 6);
        assertEquals(List.of(O, O.above(), O.east(), O.east().above(), O.east(2), O.east(2).above()), result);
        List<BlockPos> down = VeinShapes.collect(VeinShape.MINING_TUNNEL, O, Direction.UP, Direction.EAST, NONE, ALL, 3);
        assertEquals(List.of(O, O.below(), O.below(2)), down);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void largeTunnelIsThreeByThree(Direction face) {
        List<BlockPos> result = VeinShapes.collect(VeinShape.LARGE_TUNNEL, O, face, Direction.NORTH, NONE, ALL, 18);
        assertEquals(18, result.size());
        Direction into = face.getOpposite();
        Set<BlockPos> firstLayer = new HashSet<>();
        Set<BlockPos> secondLayer = new HashSet<>();
        for (BlockPos p : result) {
            int depth = p.subtract(O).get(into.getAxis()) * into.getAxisDirection().getStep();
            assertTrue(depth == 0 || depth == 1, "depth " + depth + " for " + p);
            (depth == 0 ? firstLayer : secondLayer).add(p);
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (axis != into.getAxis()) {
                    assertTrue(Math.abs(p.subtract(O).get(axis)) <= 1);
                }
            }
        }
        assertEquals(9, firstLayer.size());
        assertEquals(9, secondLayer.size());
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void smallSquareLiesOnTheFace(Direction face) {
        List<BlockPos> result = VeinShapes.collect(VeinShape.SMALL_SQUARE, O, face, Direction.NORTH, NONE, ALL, 64);
        assertEquals(9, result.size());
        assertEquals(O, result.getFirst());
        for (BlockPos p : result) {
            assertEquals(0, p.subtract(O).get(face.getAxis()), "flat on the " + face + " face");
        }
        assertEquals(4, VeinShapes.collect(VeinShape.SMALL_SQUARE, O, face, Direction.NORTH, NONE, ALL, 4).size());
    }

    @Test
    void escapeTunnelClimbsAway() {
        List<BlockPos> result = VeinShapes.collect(VeinShape.ESCAPE_TUNNEL, O, Direction.NORTH, Direction.EAST, NONE, ALL, 9);
        assertEquals(9, result.size());
        assertEquals(O, result.getFirst());
        assertTrue(result.contains(O.above()));
        assertTrue(result.contains(O.east().above()));
        assertTrue(result.contains(O.east(2).above(2)));
        for (BlockPos p : result) {
            int forward = p.getX() - O.getX();
            assertTrue(p.getZ() == O.getZ());
            assertTrue(p.getY() - O.getY() >= forward && p.getY() - O.getY() <= forward + 2, p.toString());
        }
    }

    @Test
    void shapedModesIgnoreUnbreakableBlocks() {
        Predicate<BlockPos> onlyOrigin = p -> p.equals(O);
        for (VeinShape shape : VeinShape.values()) {
            if (shape.shaped()) {
                assertEquals(List.of(O), VeinShapes.collect(shape, O, Direction.UP, Direction.NORTH, NONE, onlyOrigin, 64), shape.id());
            }
        }
    }

    @Test
    void shapeIndexWraps() {
        assertEquals(VeinShape.SHAPELESS, VeinShape.byIndex(0));
        assertEquals(VeinShape.values()[VeinShape.values().length - 1], VeinShape.byIndex(-1));
        assertEquals(VeinShape.SHAPELESS, VeinShape.byIndex(VeinShape.values().length));
        assertFalse(VeinShape.SHAPELESS.shaped());
    }

    @Test
    void mergeTagPatterns() {
        assertTrue(VeinRules.matchesPattern("c:ores/iron", "c:ores/*"));
        assertTrue(VeinRules.matchesPattern("c:ores", "c:ores/*"));
        assertFalse(VeinRules.matchesPattern("c:ores_in_ground/stone", "c:ores/*"));
        assertTrue(VeinRules.matchesPattern("minecraft:logs", "minecraft:logs"));
        assertFalse(VeinRules.matchesPattern("minecraft:logs_that_burn", "minecraft:logs"));
    }
}
