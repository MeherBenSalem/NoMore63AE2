package dev.nightbeam.corerpg.quests.util;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilTest {
    @Test
    void lenientParsingAcceptsComments() {
        JsonObject o = JsonUtil.parse("{ // comment\n \"a\": 1, /* block */ \"b\": \"x\" }").getAsJsonObject();
        assertEquals(1, o.get("a").getAsInt());
        assertEquals("x", o.get("b").getAsString());
    }

    @Test
    void gzipRoundTrip() {
        String text = "{\"quest\":\"é ✔ 日本\"}".repeat(200);
        byte[] packed = JsonUtil.gzip(text);
        assertTrue(packed.length < text.length());
        assertEquals(text, JsonUtil.gunzip(packed));
    }

    @Test
    void atomicWriteLeavesNoTempFile(@TempDir Path dir) throws Exception {
        JsonObject o = new JsonObject();
        o.addProperty("k", "v");
        Path file = dir.resolve("sub/file.json");
        JsonUtil.write(file, o);
        assertEquals("v", JsonUtil.readObject(file).get("k").getAsString());
        assertFalse(Files.exists(dir.resolve("sub/file.json.tmp")));
    }

    @Test
    void typedGettersFallBack() {
        JsonObject o = JsonUtil.parse("{\"s\":\"x\",\"n\":5,\"b\":true,\"o\":{}}").getAsJsonObject();
        assertEquals("x", JsonUtil.getString(o, "s", "d"));
        assertEquals("d", JsonUtil.getString(o, "missing", "d"));
        assertEquals(5, JsonUtil.getInt(o, "n", 0));
        assertEquals(5L, JsonUtil.getLong(o, "n", 0));
        assertTrue(JsonUtil.getBool(o, "b", false));
        assertEquals(0, JsonUtil.getObject(o, "missing").size());
        assertEquals("d", JsonUtil.getString(o, "o", "d"));
    }
}
