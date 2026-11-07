package dev.nightbeam.corerpg.quests.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class JsonUtil {
    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private JsonUtil() {
    }

    @SuppressWarnings("deprecation")
    /** Parses leniently (comments and trailing commas are fine in hand-written files). */
    public static JsonElement parse(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }

    public static JsonObject readObject(Path file) throws IOException {
        JsonElement element = parse(Files.readString(file, StandardCharsets.UTF_8));
        if (!element.isJsonObject()) {
            throw new IOException(file + " is not a JSON object");
        }
        return element.getAsJsonObject();
    }

    /** Writes atomically: a crash mid-write never leaves a half-written quest file. */
    public static void write(Path file, JsonElement json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, PRETTY.toJson(json) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static byte[] gzip(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, text.length() / 4));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static String gunzip(byte[] data) {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return new String(gz.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String getString(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    public static long getLong(JsonObject o, String key, long def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : def;
    }

    public static int getInt(JsonObject o, String key, int def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
    }

    public static boolean getBool(JsonObject o, String key, boolean def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : def;
    }

    public static JsonObject getObject(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : new JsonObject();
    }
}
