package dev.nightbeam.corerpg.quests.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSpecTest {
    enum Mode {
        FAST, SLOW
    }

    private static final class Spec {
        final ConfigSpec spec = new ConfigSpec("test.toml", "Header line one\nHeader line two");
        final ConfigSpec.Value<Boolean> flag = spec.bool("general", "flag", true, "A flag.");
        final ConfigSpec.Value<Integer> count = spec.integer("general", "count", 5, 0, 10, "A count.");
        final ConfigSpec.Value<Double> ratio = spec.decimal("numbers", "ratio", 0.5, 0, 1, "A ratio.");
        final ConfigSpec.Value<String> name = spec.string("numbers", "name", "default # not a comment", "A name.");
        final ConfigSpec.Value<Mode> mode = spec.enumValue("numbers", "mode", Mode.FAST, "A mode.");
        final ConfigSpec.Value<List<String>> tags = spec.stringList("lists", "tags", List.of("c:ores/*", "minecraft:logs"), "Tags.\nSecond line.");
    }

    @Test
    void writeContainsCommentsAndSections() {
        Spec s = new Spec();
        String text = s.spec.write();
        assertTrue(text.startsWith("# Header line one\n# Header line two\n"));
        assertTrue(text.contains("[general]\n# A flag.\nflag = true\n"));
        assertTrue(text.contains("# A count. (0 to 10)\ncount = 5"));
        assertTrue(text.contains("# Tags.\n# Second line.\ntags = [\"c:ores/*\", \"minecraft:logs\"]"));
        assertTrue(text.contains("mode = \"fast\""));
    }

    @Test
    void roundTrip() {
        Spec a = new Spec();
        a.flag.set(false);
        a.count.set(9);
        a.ratio.set(0.25);
        a.name.set("quote \" and # hash");
        a.mode.set(Mode.SLOW);
        a.tags.set(List.of("a,b", "c"));
        Spec b = new Spec();
        b.spec.read(List.of(a.spec.write().split("\n")));
        assertFalse(b.flag.get());
        assertEquals(9, b.count.get());
        assertEquals(0.25, b.ratio.get());
        assertEquals("quote \" and # hash", b.name.get());
        assertEquals(Mode.SLOW, b.mode.get());
        assertEquals(List.of("a,b", "c"), b.tags.get());
    }

    @Test
    void badValuesFallBackAndRangesClamp() {
        Spec s = new Spec();
        s.spec.read(List.of(
                "[general]",
                "flag = maybe",
                "count = 99   # clamped",
                "[numbers]",
                "ratio = abc",
                "mode = \"turbo\"",
                "unknown = 1",
                "[lists]",
                "tags = not-a-list"));
        assertTrue(s.flag.get());
        assertEquals(10, s.count.get());
        assertEquals(0.5, s.ratio.get());
        assertEquals(Mode.FAST, s.mode.get());
        assertEquals(List.of("c:ores/*", "minecraft:logs"), s.tags.get());
    }

    @Test
    void emptyListAndSectionScoping() {
        Spec s = new Spec();
        s.spec.read(List.of("[lists]", "tags = []", "[general]", "ratio = 0.9"));
        assertEquals(List.of(), s.tags.get());
        assertEquals(0.5, s.ratio.get(), "ratio is in [numbers], not [general]");
    }

    @Test
    void loadCreatesAndKeepsValues(@TempDir Path dir) throws Exception {
        Spec s = new Spec();
        s.spec.load(dir);
        Path file = dir.resolve("test.toml");
        assertTrue(Files.exists(file));
        Files.writeString(file, Files.readString(file).replace("count = 5", "count = 7"));
        Spec t = new Spec();
        t.spec.load(dir);
        assertEquals(7, t.count.get());
        assertTrue(Files.readString(file).contains("count = 7"));
    }

    @Test
    void jsonRoundTrip() {
        Spec a = new Spec();
        a.count.set(3);
        a.tags.set(List.of("x"));
        Spec b = new Spec();
        b.spec.fromJson(a.spec.toJson());
        assertEquals(3, b.count.get());
        assertEquals(List.of("x"), b.tags.get());
    }

    @Test
    void realSpecsWrite() {
        assertTrue(CoreConfig.SERVER.spec.write().contains("[vein_mining]"));
        assertTrue(CoreConfig.CLIENT.spec.write().contains("tracker_corner = \"top_left\""));
    }
}
