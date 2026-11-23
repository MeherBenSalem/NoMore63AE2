package dev.nightbeam.corerpg.quests.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdsTest {
    @Test
    void validIds() {
        assertTrue(Ids.isValid("a"));
        assertTrue(Ids.isValid("act1/kindle"));
        assertTrue(Ids.isValid("a-b_c.d/e9"));
        assertTrue(Ids.isValid("x".repeat(64)));
    }

    @Test
    void invalidIds() {
        assertFalse(Ids.isValid(null));
        assertFalse(Ids.isValid(""));
        assertFalse(Ids.isValid("Upper"));
        assertFalse(Ids.isValid("has space"));
        assertFalse(Ids.isValid("colon:id"));
        assertFalse(Ids.isValid("x".repeat(65)));
    }

    @Test
    void sanitizeMakesValidIds() {
        assertEquals("the_armory", Ids.sanitize("&eThe Armory!"));
        assertEquals("id", Ids.sanitize("!!!"));
        assertEquals(64, Ids.sanitize("a".repeat(100)).length());
        assertTrue(Ids.isValid(Ids.sanitize("Ünïcödé  quest -- name")));
    }

    @Test
    void randomIdsAreValidHex() {
        for (int i = 0; i < 100; i++) {
            String id = Ids.random();
            assertEquals(8, id.length());
            assertTrue(id.matches("[0-9a-f]{8}"));
        }
    }

    @Test
    void uniqueAvoidsTakenIds() {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String id = Ids.unique(taken::contains);
            assertTrue(taken.add(id));
        }
    }

    @Test
    void uniqueFromAddsSuffix() {
        Set<String> taken = new HashSet<>(Set.of("armory", "armory_2"));
        assertEquals("armory_3", Ids.uniqueFrom("Armory", taken::contains));
        assertEquals("forge", Ids.uniqueFrom("Forge", taken::contains));
        String longBase = "b".repeat(64);
        taken.add(longBase);
        String next = Ids.uniqueFrom(longBase, taken::contains);
        assertTrue(Ids.isValid(next));
        assertTrue(next.endsWith("_2"));
    }
}
