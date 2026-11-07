package dev.nightbeam.corerpg.quests.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nightbeam.corerpg.quests.book.Icon;
import dev.nightbeam.corerpg.quests.filter.FilterParser;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.util.Ids;
import dev.nightbeam.corerpg.quests.util.ItemJson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The property value types. */
public final class PropTypes {
    private PropTypes() {
    }

    public static final PropType<String> STRING = new PropType<>(EditorKind.STRING) {
        @Override
        public JsonElement write(String value, JsonCtx ctx) {
            return new JsonPrimitive(value);
        }

        @Override
        public String read(JsonElement json, JsonCtx ctx) {
            return json.getAsString();
        }
    };

    /** A filter expression; validated with the filter parser. */
    public static final PropType<String> FILTER = new PropType<>(EditorKind.FILTER) {
        @Override
        public JsonElement write(String value, JsonCtx ctx) {
            return new JsonPrimitive(value);
        }

        @Override
        public String read(JsonElement json, JsonCtx ctx) {
            return json.getAsString();
        }

        @Override
        public @Nullable String validate(String value) {
            return value.isBlank() ? null : FilterParser.validate(value);
        }
    };

    /** Multi-line text, stored as an array of lines (a plain string is accepted too). */
    public static final PropType<List<String>> TEXT = new PropType<>(EditorKind.TEXT) {
        @Override
        public JsonElement write(List<String> value, JsonCtx ctx) {
            JsonArray array = new JsonArray();
            value.forEach(array::add);
            return array;
        }

        @Override
        public List<String> read(JsonElement json, JsonCtx ctx) {
            List<String> lines = new ArrayList<>();
            if (json.isJsonArray()) {
                json.getAsJsonArray().forEach(e -> lines.add(e.getAsString()));
            } else {
                lines.addAll(List.of(json.getAsString().split("\n", -1)));
            }
            return lines;
        }

        @Override
        public List<String> copy(List<String> value) {
            return new ArrayList<>(value);
        }
    };

    public static final PropType<List<String>> STRING_LIST = listOf(EditorKind.STRING_LIST);

    public static final PropType<List<String>> ID_LIST = new PropType<>(EditorKind.ID_LIST) {
        @Override
        public JsonElement write(List<String> value, JsonCtx ctx) {
            JsonArray array = new JsonArray();
            value.forEach(array::add);
            return array;
        }

        @Override
        public List<String> read(JsonElement json, JsonCtx ctx) {
            List<String> ids = new ArrayList<>();
            if (json.isJsonArray()) {
                json.getAsJsonArray().forEach(e -> ids.add(e.getAsString()));
            } else {
                ids.add(json.getAsString());
            }
            return ids;
        }

        @Override
        public List<String> copy(List<String> value) {
            return new ArrayList<>(value);
        }

        @Override
        public @Nullable String validate(List<String> value) {
            for (String id : value) {
                if (!Ids.isValid(id)) {
                    return "Invalid id: " + id;
                }
            }
            return null;
        }
    };

    public static final PropType<String> ID = new PropType<>(EditorKind.ID_REF) {
        @Override
        public JsonElement write(String value, JsonCtx ctx) {
            return new JsonPrimitive(value);
        }

        @Override
        public String read(JsonElement json, JsonCtx ctx) {
            return json.getAsString();
        }

        @Override
        public @Nullable String validate(String value) {
            return value.isEmpty() || Ids.isValid(value) ? null : "Invalid id: " + value;
        }
    };

    public static final PropType<Boolean> BOOL = new PropType<>(EditorKind.BOOL) {
        @Override
        public JsonElement write(Boolean value, JsonCtx ctx) {
            return new JsonPrimitive(value);
        }

        @Override
        public Boolean read(JsonElement json, JsonCtx ctx) {
            return json.getAsBoolean();
        }
    };

    public static final PropType<Integer> COLOR = new PropType<>(EditorKind.COLOR) {
        @Override
        public JsonElement write(Integer value, JsonCtx ctx) {
            return new JsonPrimitive(String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF));
        }

        @Override
        public Integer read(JsonElement json, JsonCtx ctx) {
            if (json.getAsJsonPrimitive().isNumber()) {
                return json.getAsInt() & 0xFFFFFF;
            }
            String s = json.getAsString().trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            return Integer.parseUnsignedInt(s, 16) & 0xFFFFFF;
        }
    };

    public static final PropType<ItemStack> ITEM_STACK = new PropType<>(EditorKind.ITEM_STACK) {
        @Override
        public JsonElement write(ItemStack value, JsonCtx ctx) {
            return ItemJson.write(value, ctx);
        }

        @Override
        public ItemStack read(JsonElement json, JsonCtx ctx) {
            return ItemJson.read(json, ctx);
        }

        @Override
        public ItemStack copy(ItemStack value) {
            return value.copy();
        }

        @Override
        public boolean same(@Nullable ItemStack a, @Nullable ItemStack b) {
            if (a == null || b == null) {
                return a == b;
            }
            return ItemStack.matches(a, b);
        }
    };

    public static final PropType<List<ItemStack>> ITEM_LIST = new PropType<>(EditorKind.ITEM_LIST) {
        @Override
        public JsonElement write(List<ItemStack> value, JsonCtx ctx) {
            JsonArray array = new JsonArray();
            value.forEach(stack -> array.add(ItemJson.write(stack, ctx)));
            return array;
        }

        @Override
        public List<ItemStack> read(JsonElement json, JsonCtx ctx) {
            List<ItemStack> list = new ArrayList<>();
            json.getAsJsonArray().forEach(e -> {
                ItemStack stack = ItemJson.read(e, ctx);
                if (!stack.isEmpty()) {
                    list.add(stack);
                }
            });
            return list;
        }

        @Override
        public List<ItemStack> copy(List<ItemStack> value) {
            List<ItemStack> list = new ArrayList<>(value.size());
            value.forEach(stack -> list.add(stack.copy()));
            return list;
        }

        @Override
        public boolean same(@Nullable List<ItemStack> a, @Nullable List<ItemStack> b) {
            if (a == null || b == null) {
                return a == b;
            }
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!ItemStack.matches(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
    };

    public static final PropType<Icon> ICON = new PropType<>(EditorKind.ICON) {
        @Override
        public JsonElement write(Icon value, JsonCtx ctx) {
            return value.toJson(ctx);
        }

        @Override
        public Icon read(JsonElement json, JsonCtx ctx) {
            return Icon.fromJson(json, ctx);
        }

        @Override
        public Icon copy(Icon value) {
            return value.copy();
        }
    };

    public static PropType<Integer> intRange(int min, int max) {
        return new PropType<>(EditorKind.INT) {
            @Override
            public JsonElement write(Integer value, JsonCtx ctx) {
                return new JsonPrimitive(value);
            }

            @Override
            public Integer read(JsonElement json, JsonCtx ctx) {
                return Math.clamp(json.getAsLong(), min, max);
            }

            @Override
            public @Nullable String validate(Integer value) {
                return value < min || value > max ? "Must be between " + min + " and " + max : null;
            }
        };
    }

    public static PropType<Long> longRange(long min, long max) {
        return new PropType<>(EditorKind.LONG) {
            @Override
            public JsonElement write(Long value, JsonCtx ctx) {
                return new JsonPrimitive(value);
            }

            @Override
            public Long read(JsonElement json, JsonCtx ctx) {
                return Math.max(min, Math.min(max, json.getAsLong()));
            }

            @Override
            public @Nullable String validate(Long value) {
                return value < min || value > max ? "Must be between " + min + " and " + max : null;
            }
        };
    }

    public static PropType<Double> doubleRange(double min, double max) {
        return new PropType<>(EditorKind.DOUBLE) {
            @Override
            public JsonElement write(Double value, JsonCtx ctx) {
                double rounded = Math.round(value * 10000.0) / 10000.0;
                if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15) {
                    return new JsonPrimitive((long) rounded);
                }
                return new JsonPrimitive(rounded);
            }

            @Override
            public Double read(JsonElement json, JsonCtx ctx) {
                return Math.clamp(json.getAsDouble(), min, max);
            }

            @Override
            public @Nullable String validate(Double value) {
                return value < min || value > max || value.isNaN() ? "Must be between " + min + " and " + max : null;
            }
        };
    }

    public static <E extends Enum<E> & StringRepresentable> EnumType<E> enumType(Class<E> clazz) {
        return new EnumType<>(clazz);
    }

    public static PropType<ResourceLocation> resource(ResourceKind kind) {
        return new ResourceType(kind);
    }

    /** A registry id or tag reference; null means "any". */
    public static RefType ref(ResourceKind kind) {
        return new RefType(kind);
    }

    public static final class RefType extends PropType<Ref> {
        private final ResourceKind kind;

        RefType(ResourceKind kind) {
            super(EditorKind.RESOURCE);
            this.kind = kind;
        }

        public ResourceKind kind() {
            return kind;
        }

        @Override
        public JsonElement write(Ref value, JsonCtx ctx) {
            return new JsonPrimitive(value == null ? "" : value.toString());
        }

        @Override
        public Ref read(JsonElement json, JsonCtx ctx) {
            String s = json.getAsString();
            if (s.isBlank()) {
                return null;
            }
            Ref ref = Ref.parse(s);
            if (ref == null) {
                throw new IllegalArgumentException("Invalid id or tag '" + s + "'");
            }
            return ref;
        }
    }

    private static PropType<List<String>> listOf(EditorKind kind) {
        return new PropType<>(kind) {
            @Override
            public JsonElement write(List<String> value, JsonCtx ctx) {
                JsonArray array = new JsonArray();
                value.forEach(array::add);
                return array;
            }

            @Override
            public List<String> read(JsonElement json, JsonCtx ctx) {
                List<String> list = new ArrayList<>();
                if (json.isJsonArray()) {
                    json.getAsJsonArray().forEach(e -> list.add(e.getAsString()));
                } else {
                    list.add(json.getAsString());
                }
                return list;
            }

            @Override
            public List<String> copy(List<String> value) {
                return new ArrayList<>(value);
            }
        };
    }

    public static final class EnumType<E extends Enum<E> & StringRepresentable> extends PropType<E> {
        private final Class<E> clazz;

        EnumType(Class<E> clazz) {
            super(EditorKind.ENUM);
            this.clazz = clazz;
        }

        public E[] values() {
            return clazz.getEnumConstants();
        }

        @Override
        public JsonElement write(E value, JsonCtx ctx) {
            return new JsonPrimitive(value.getSerializedName());
        }

        @Override
        public E read(JsonElement json, JsonCtx ctx) {
            String name = json.getAsString();
            for (E e : clazz.getEnumConstants()) {
                if (e.getSerializedName().equalsIgnoreCase(name)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown value '" + name + "'");
        }
    }

    public static final class ResourceType extends PropType<ResourceLocation> {
        private final ResourceKind kind;

        ResourceType(ResourceKind kind) {
            super(EditorKind.RESOURCE);
            this.kind = kind;
        }

        public ResourceKind kind() {
            return kind;
        }

        @Override
        public JsonElement write(ResourceLocation value, JsonCtx ctx) {
            return new JsonPrimitive(kind.tag ? "#" + value : value.toString());
        }

        @Override
        public ResourceLocation read(JsonElement json, JsonCtx ctx) {
            String s = json.getAsString().trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id == null) {
                throw new IllegalArgumentException("Invalid resource location '" + s + "'");
            }
            return id;
        }
    }
}
