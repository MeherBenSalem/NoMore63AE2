package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.McTest;
import dev.nightbeam.corerpg.quests.reward.Reward;
import dev.nightbeam.corerpg.quests.reward.RewardType;
import dev.nightbeam.corerpg.quests.reward.RewardTypes;
import dev.nightbeam.corerpg.quests.reward.types.TableReward;
import dev.nightbeam.corerpg.quests.reward.types.UnknownReward;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.schema.Prop;
import dev.nightbeam.corerpg.quests.schema.PropType;
import dev.nightbeam.corerpg.quests.schema.PropTypes;
import dev.nightbeam.corerpg.quests.task.Ref;
import dev.nightbeam.corerpg.quests.task.Task;
import dev.nightbeam.corerpg.quests.task.TaskType;
import dev.nightbeam.corerpg.quests.task.TaskTypes;
import dev.nightbeam.corerpg.quests.task.types.UnknownTask;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every property of every object type, set to a non-default value, survives writing and reading. This guards the
 * single serialisation path shared by files, network sync and the editor.
 */
class SchemaRoundTripTest extends McTest {
    static JsonCtx ctx() {
        return new JsonCtx(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /** Sets every property to a valid value that differs from its default. */
    static void mutateAll(QuestObject object) {
        for (Prop<?> prop : object.props()) {
            mutate(prop);
            assertFalse(prop.isDefault(), object.describe() + "." + prop.key() + " should now differ from its default");
            assertEquals(null, validate(prop), object.describe() + "." + prop.key());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> String validate(Prop<T> prop) {
        return prop.type().validate(prop.get());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void mutate(Prop<?> raw) {
        Prop prop = raw;
        PropType<?> type = prop.type();
        String key = prop.key();
        Object def = prop.defaultValue();
        Object value = switch (type.editorKind()) {
            case STRING -> "&6value of " + key;
            case FILTER -> "any(minecraft:stone, #c:ingots)";
            case TEXT -> List.of("&6Line one of " + key, "", "line three");
            case STRING_LIST -> List.of("alpha", "beta");
            case ID_LIST -> List.of("dep_one", "dep_two");
            case ID_REF -> "ref_" + key.replace('_', '-');
            case BOOL -> !(Boolean) def;
            case COLOR -> 0x123456;
            case ICON -> Icon.of(Items.GOLD_INGOT);
            case ITEM_STACK -> {
                ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("Named " + key));
                yield stack;
            }
            case ITEM_LIST -> List.of(new ItemStack(Items.STONE), new ItemStack(Items.DIAMOND, 2));
            case ENUM -> {
                Object[] values = ((PropTypes.EnumType) type).values();
                Object current = prop.get();
                Object pick = null;
                for (Object v : values) {
                    if (!v.equals(def) && !v.equals(current) && !"default".equals(((net.minecraft.util.StringRepresentable) v).getSerializedName())) {
                        pick = v;
                        break;
                    }
                }
                if (pick == null) {
                    for (Object v : values) {
                        if (!v.equals(def)) {
                            pick = v;
                            break;
                        }
                    }
                }
                yield pick;
            }
            case RESOURCE -> type instanceof PropTypes.RefType
                    ? new Ref(ResourceLocation.parse("test:ref_" + key), true)
                    : ResourceLocation.parse("test:res_" + key);
            case INT -> pickNumber(prop, def, true);
            case LONG -> pickNumber(prop, def, false);
            case DOUBLE -> {
                double d = (Double) def;
                double candidate = d + 0.75;
                if (prop.type().validate(candidate) != null) {
                    candidate = d - 0.75;
                }
                if (prop.type().validate(candidate) != null) {
                    candidate = d + 0.0625;
                }
                yield candidate;
            }
        };
        prop.set(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object pickNumber(Prop prop, Object def, boolean isInt) {
        long d = ((Number) def).longValue();
        for (long candidate : new long[]{d + 3, d + 1, d - 1}) {
            Object boxed = isInt ? (Object) (int) candidate : (Object) candidate;
            if (prop.type().validate(boxed) == null) {
                return boxed;
            }
        }
        throw new AssertionError("No valid number for " + prop.key());
    }

    static QuestBook fullBook() {
        QuestBook book = new QuestBook();
        mutateAll(book);
        book.progressionMode = ProgressionMode.FLEXIBLE;

        ChapterGroup group = new ChapterGroup(book);
        group.setIdInternal("group_a");
        mutateAll(group);
        book.groups.add(group);

        Chapter chapter = new Chapter(book);
        chapter.setIdInternal("act1/start");
        mutateAll(chapter);
        book.chapters.add(chapter);

        Quest quest = new Quest(chapter);
        quest.setIdInternal("act1/kindle");
        mutateAll(quest);
        chapter.quests.add(quest);

        int n = 0;
        for (TaskType<?> type : TaskTypes.all()) {
            Task task = type.create();
            task.setIdInternal("task_" + n++);
            mutateAll(task);
            task.quest = quest;
            quest.tasks.add(task);
        }
        for (RewardType<?> type : RewardTypes.all()) {
            Reward reward = type.create();
            reward.setIdInternal("reward_" + n++);
            mutateAll(reward);
            reward.quest = quest;
            quest.rewards.add(reward);
        }

        QuestLink link = new QuestLink(chapter);
        link.setIdInternal("link_a");
        mutateAll(link);
        chapter.links.add(link);

        ChapterImage image = new ChapterImage(chapter);
        image.setIdInternal("image_a");
        mutateAll(image);
        chapter.images.add(image);

        RewardTable table = new RewardTable(book);
        table.setIdInternal("table_a");
        mutateAll(table);
        for (RewardType<?> type : RewardTypes.all()) {
            Reward reward = type.create();
            reward.setIdInternal("entry_" + n++);
            mutateAll(reward);
            reward.table = table;
            table.entries.add(new RewardTable.Entry(1.5 + n, reward));
        }
        book.tables.add(table);
        book.rebuildIndex();
        return book;
    }

    @Test
    void everyPropertyOfEveryObjectRoundTrips() {
        JsonCtx ctx = ctx();
        QuestBook book = fullBook();
        JsonObject first = book.toJson(ctx);

        QuestBook copy = new QuestBook();
        copy.fromJson(JsonUtil.parse(JsonUtil.COMPACT.toJson(first)).getAsJsonObject(), ctx);
        JsonObject second = copy.toJson(ctx);
        assertEquals(first, second);

        Quest quest = copy.quest("act1/kindle");
        assertNotNull(quest);
        assertEquals(TaskTypes.all().size(), quest.tasks.size());
        assertEquals(RewardTypes.all().size(), quest.rewards.size());
        assertEquals(RewardTypes.all().size(), copy.table("table_a").entries.size());
        assertEquals(book.objectCount(), copy.objectCount());
        // Property by property, the copy holds the same values.
        assertSameProps(book, copy);
        assertSameProps(book.quest("act1/kindle"), quest);
        for (int i = 0; i < quest.tasks.size(); i++) {
            assertEquals(book.quest("act1/kindle").tasks.get(i).type(), quest.tasks.get(i).type());
            assertSameProps(book.quest("act1/kindle").tasks.get(i), quest.tasks.get(i));
        }
        for (int i = 0; i < quest.rewards.size(); i++) {
            assertSameProps(book.quest("act1/kindle").rewards.get(i), quest.rewards.get(i));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertSameProps(QuestObject a, QuestObject b) {
        List<Prop<?>> pa = a.props().list();
        List<Prop<?>> pb = b.props().list();
        assertEquals(pa.size(), pb.size());
        for (int i = 0; i < pa.size(); i++) {
            Prop x = pa.get(i);
            Prop y = pb.get(i);
            assertTrue(x.type().same(x.get(), y.get()), a.describe() + "." + x.key() + ": " + x.get() + " vs " + y.get());
        }
    }

    @Test
    void defaultsAreNotWritten() {
        JsonCtx ctx = ctx();
        Quest quest = new Quest(new Chapter(new QuestBook()));
        quest.setIdInternal("plain");
        JsonObject json = quest.propsToJson(ctx);
        assertEquals(1, json.size(), "only the id: " + json);
        Task item = TaskTypes.ITEM.create();
        item.setIdInternal("t");
        JsonObject taskJson = item.propsToJson(ctx);
        assertEquals("item", taskJson.get("type").getAsString());
        assertEquals(2, taskJson.size(), taskJson.toString());
    }

    @Test
    void readFillsDefaultsAndSurvivesBadValues() {
        JsonCtx ctx = ctx();
        JsonObject json = JsonUtil.parse("""
                {"id":"q","title":"T","x":"not a number","shape":"blob","size":99,"dependencies":"single","repeat_cooldown":-5,
                 "tasks":[{"id":"t1","type":"kill","entity":"#minecraft:raiders","value":3},
                          {"id":"t2","type":"othermod:magic","power":9001}],
                 "rewards":[{"id":"r1","type":"xp_levels","levels":7},{"id":"r2","type":"othermod:gift","x":[1,2]}]}
                """).getAsJsonObject();
        Quest q = Quest.read(new Chapter(new QuestBook()), json, ctx);
        assertEquals("T", q.title);
        assertEquals(0, q.x, "invalid number falls back to the default");
        assertEquals(QuestShape.DEFAULT, q.shape, "unknown enum falls back to the default");
        assertEquals(8, q.size, "out-of-range values are clamped");
        assertEquals(List.of("single"), q.dependencies, "a single id is accepted for a list");
        assertEquals(0, q.repeatCooldown);
        assertEquals(2, q.tasks.size());
        assertEquals(new Ref(ResourceLocation.parse("minecraft:raiders"), true), ((dev.nightbeam.corerpg.quests.task.types.KillTask) q.tasks.get(0)).entity);
        assertInstanceOf(UnknownTask.class, q.tasks.get(1));
        assertInstanceOf(UnknownReward.class, q.rewards.get(1));
        // Unknown types are kept exactly, so removing an addon never loses book data.
        JsonObject written = q.toJson(ctx);
        assertEquals(json.getAsJsonArray("tasks").get(1), written.getAsJsonArray("tasks").get(1));
        assertEquals(json.getAsJsonArray("rewards").get(1), written.getAsJsonArray("rewards").get(1));
    }

    @Test
    void duplicateAndInvalidIdsAreReplaced() {
        QuestBook book = new QuestBook();
        Chapter c = dev.nightbeam.corerpg.quests.TestBooks.chapter(book, "same");
        Quest q1 = dev.nightbeam.corerpg.quests.TestBooks.quest(c, "same");
        Quest q2 = dev.nightbeam.corerpg.quests.TestBooks.quest(c, "Not Valid");
        book.rebuildIndex();
        assertEquals("same", c.id());
        assertNotEquals("same", q1.id());
        assertTrue(dev.nightbeam.corerpg.quests.util.Ids.isValid(q2.id()));
        assertEquals(q1, book.quest(q1.id()));
    }

    @Test
    void tableRollsAndChances() {
        RewardTable table = fullBook().table("table_a");
        table.emptyWeight = 0;
        table.rolls = 5;
        double sum = 0;
        for (RewardTable.Entry e : table.entries) {
            sum += table.chance(e);
        }
        assertEquals(100.0, sum, 1e-9);
        assertEquals(5, table.roll(net.minecraft.util.RandomSource.create(1)).size());
        table.entries.forEach(e -> e.weight = 0);
        table.emptyWeight = 1;
        assertTrue(table.roll(net.minecraft.util.RandomSource.create(1)).isEmpty(), "only the empty weight can win");
    }

    @Test
    void tableRewardFindsItsTable() {
        QuestBook book = fullBook();
        TableReward reward = (TableReward) book.quest("act1/kindle").rewards.stream().filter(r -> r instanceof TableReward).findFirst().orElseThrow();
        reward.tableId = "table_a";
        book.table("table_a").hideTooltip = false;
        assertEquals(book.table("table_a"), reward.table());
        List<Component> lines = new ArrayList<>();
        reward.addTooltip(lines);
        assertFalse(lines.isEmpty());
    }
}
