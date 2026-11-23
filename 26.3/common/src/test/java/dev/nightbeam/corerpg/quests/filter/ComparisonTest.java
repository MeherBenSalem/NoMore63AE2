package dev.nightbeam.corerpg.quests.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonTest {
    @Test
    void operators() {
        assertTrue(Comparison.parse(">=4", 0).test(4));
        assertFalse(Comparison.parse(">=4", 0).test(3));
        assertTrue(Comparison.parse(">4", 0).test(5));
        assertFalse(Comparison.parse(">4", 0).test(4));
        assertTrue(Comparison.parse("<10", 0).test(9));
        assertTrue(Comparison.parse("<=10", 0).test(10));
        assertTrue(Comparison.parse("=3", 0).test(3));
        assertTrue(Comparison.parse("==3", 0).test(3));
        assertTrue(Comparison.parse("3", 0).test(3));
        assertTrue(Comparison.parse("!=0", 0).test(1));
        assertFalse(Comparison.parse("!=0", 0).test(0));
    }

    @Test
    void rangesAndPercent() {
        Comparison range = Comparison.parse("5..2", 0);
        assertEquals(Comparison.Op.RANGE, range.op());
        assertTrue(range.test(2));
        assertTrue(range.test(5));
        assertFalse(range.test(5.5));
        Comparison pct = Comparison.parse(" < 25 % ", 0);
        assertTrue(pct.percent());
        assertTrue(pct.test(10));
    }

    @Test
    void toStringRoundTrips() {
        for (String s : new String[]{">=4", "<10", "=3", "!=0", "2..5", "<25%", ">=0.5"}) {
            Comparison c = Comparison.parse(s, 0);
            assertEquals(c, Comparison.parse(c.toString(), 0), s);
        }
        assertEquals("=3", Comparison.parse("3", 0).toString());
    }

    @Test
    void errorsCarryPosition() {
        FilterException e = assertThrows(FilterException.class, () -> Comparison.parse("lots", 7));
        assertEquals(7, e.position());
        assertTrue(e.getMessage().contains("(at 8)"));
    }
}
