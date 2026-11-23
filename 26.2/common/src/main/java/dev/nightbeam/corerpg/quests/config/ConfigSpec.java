package dev.nightbeam.corerpg.quests.config;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * A commented settings file in a small TOML subset: [sections], key = value, booleans, numbers, "strings" and
 * ["string", "lists"]. Unknown keys are ignored; missing keys get their defaults; the file is rewritten so new
 * settings and their comments appear after an update.
 */
public final class ConfigSpec {
    private final String fileName;
    private final String header;
    private final List<Value<?>> values = new ArrayList<>();

    public ConfigSpec(String fileName, String header) {
        this.fileName = fileName;
        this.header = header;
    }

    public String fileName() {
        return fileName;
    }

    public List<Value<?>> values() {
        return values;
    }

    public final class Value<T> {
        private final String section;
        private final String key;
        private final String comment;
        private final T defaultValue;
        private final Function<String, T> parser;
        private final Function<T, String> writer;
        private volatile T value;

        Value(String section, String key, String comment, T defaultValue, Function<String, T> parser, Function<T, String> writer) {
            this.section = section;
            this.key = key;
            this.comment = comment;
            this.defaultValue = defaultValue;
            this.parser = parser;
            this.writer = writer;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }

        public String path() {
            return section + "." + key;
        }

        String serialized() {
            return writer.apply(value);
        }

        void parse(String text) {
            value = parser.apply(text);
        }

        void reset() {
            value = defaultValue;
        }
    }

    public Value<Boolean> bool(String section, String key, boolean def, String comment) {
        return add(new Value<>(section, key, comment, def, s -> {
            if (!s.equals("true") && !s.equals("false")) {
                throw new IllegalArgumentException("expected true or false");
            }
            return Boolean.parseBoolean(s);
        }, String::valueOf));
    }

    public Value<Integer> integer(String section, String key, int def, int min, int max, String comment) {
        return add(new Value<>(section, key, comment + " (" + min + " to " + max + ")", def,
                s -> Math.clamp(Long.parseLong(s), min, max), String::valueOf));
    }

    public Value<Double> decimal(String section, String key, double def, double min, double max, String comment) {
        return add(new Value<>(section, key, comment + " (" + min + " to " + max + ")", def,
                s -> Math.clamp(Double.parseDouble(s), min, max), d -> String.format(Locale.ROOT, "%s", d)));
    }

    public Value<String> string(String section, String key, String def, String comment) {
        return add(new Value<>(section, key, comment, def, ConfigSpec::unquote, ConfigSpec::quote));
    }

    public <E extends Enum<E>> Value<E> enumValue(String section, String key, E def, String comment) {
        Class<E> type = def.getDeclaringClass();
        StringBuilder options = new StringBuilder();
        for (E e : type.getEnumConstants()) {
            options.append(options.isEmpty() ? "" : ", ").append(e.name().toLowerCase(Locale.ROOT));
        }
        return add(new Value<>(section, key, comment + " (" + options + ")", def,
                s -> Enum.valueOf(type, unquote(s).toUpperCase(Locale.ROOT)), e -> quote(e.name().toLowerCase(Locale.ROOT))));
    }

    public Value<List<String>> stringList(String section, String key, List<String> def, String comment) {
        return add(new Value<>(section, key, comment, List.copyOf(def), ConfigSpec::parseList, list -> {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(i == 0 ? "" : ", ").append(quote(list.get(i)));
            }
            return sb.append("]").toString();
        }));
    }

    private <T> Value<T> add(Value<T> value) {
        values.add(value);
        return value;
    }

    /** Loads the file (creating or completing it) from the given directory. */
    public void load(Path dir) {
        Path file = dir.resolve(fileName);
        values.forEach(Value::reset);
        if (Files.exists(file)) {
            try {
                read(Files.readAllLines(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                CoreQuests.LOG.error("Could not read {}", file, e);
            }
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(file, write(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CoreQuests.LOG.error("Could not write {}", file, e);
        }
    }

    void read(List<String> lines) {
        Map<String, Value<?>> byPath = new LinkedHashMap<>();
        values.forEach(v -> byPath.put(v.path(), v));
        String section = "";
        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String text = line.substring(eq + 1).trim();
            Value<?> value = byPath.get(section + "." + key);
            if (value == null) {
                continue;
            }
            try {
                value.parse(text);
            } catch (RuntimeException e) {
                CoreQuests.LOG.warn("{}: bad value for {} ({}); using the default", fileName, value.path(), e.getMessage());
                value.reset();
            }
        }
    }

    String write() {
        StringBuilder sb = new StringBuilder();
        for (String line : header.split("\n")) {
            sb.append("# ").append(line).append('\n');
        }
        String section = null;
        for (Value<?> v : values) {
            if (!v.section.equals(section)) {
                section = v.section;
                sb.append('\n').append('[').append(section).append("]\n");
            }
            for (String line : v.comment.split("\n")) {
                sb.append("# ").append(line).append('\n');
            }
            sb.append(v.key).append(" = ").append(v.serialized()).append('\n');
        }
        return sb.toString();
    }

    /** The values as JSON (sent to clients for server settings they need). */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        values.forEach(v -> json.addProperty(v.path(), v.serialized()));
        return json;
    }

    public void fromJson(JsonObject json) {
        for (Value<?> v : values) {
            if (json.has(v.path())) {
                try {
                    v.parse(json.get(v.path()).getAsString());
                } catch (RuntimeException ignored) {
                    v.reset();
                }
            }
        }
    }

    private static String stripComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inString) {
                i++;
            } else if (c == '"') {
                inString = !inString;
            } else if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return t;
    }

    static List<String> parseList(String s) {
        String t = s.trim();
        if (!t.startsWith("[") || !t.endsWith("]")) {
            throw new IllegalArgumentException("expected a list like [\"a\", \"b\"]");
        }
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int i = 1; i < t.length() - 1; i++) {
            char c = t.charAt(i);
            if (c == '"' && (i == 0 || t.charAt(i - 1) != '\\')) {
                inString = !inString;
                current.append(c);
            } else if (c == ',' && !inString) {
                if (!current.toString().isBlank()) {
                    list.add(unquote(current.toString()));
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) {
            list.add(unquote(current.toString()));
        }
        return List.copyOf(list);
    }
}
