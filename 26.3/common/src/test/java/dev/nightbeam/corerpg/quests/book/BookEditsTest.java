package dev.nightbeam.corerpg.quests.book;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.McTest;
import dev.nightbeam.corerpg.quests.TestBooks;
import dev.nightbeam.corerpg.quests.reward.types.ItemReward;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.task.types.CheckmarkTask;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookEditsTest extends McTest {
    private JsonCtx ctx;
    private QuestBook book;
    private Chapter ch1;
    private Chapter ch2;
    private Quest a;
    private Quest b;

    @BeforeEach
    void setUp() {
        ctx = SchemaRoundTripTest.ctx();
        book = new QuestBook();
        ch1 = TestBooks.chapter(book, "ch1");
        ch2 = TestBooks.chapter(book, "ch2");
        a = TestBooks.quest(ch1, "a");
        TestBooks.check(a, "a_task");
        TestBooks.itemReward(a, "a_reward", 1);
        b = TestBooks.quest(ch1, "b", "a");
        TestBooks.check(b, "b_task");
        QuestLink link = new QuestLink(ch2);
        link.setIdInternal("link_to_a");
        link.quest = "a";
        ch2.links.add(link);
        ch2.autofocus = "a";
        ChapterImage image = new ChapterImage(ch1);
        image.setIdInternal("img");
        ch1.images.add(image);
        RewardTable table = new RewardTable(book);
        table.setIdInternal("table");
        book.tables.add(table);
        ChapterGroup group = new ChapterGroup(book);
        group.setIdInternal("grp");
        book.groups.add(group);
        ch2.group = "grp";
        book.rebuildIndex();
    }

    /** Applies like the server does (ids first), and replays the same op on a second copy like a client. */
    private void applyBoth(JsonObject op) {
        QuestBook client = new QuestBook();
        client.fromJson(book.toJson(ctx), ctx);
        BookEdits.assignIds(book, op);
        JsonObject wire = JsonUtil.parse(JsonUtil.COMPACT.toJson(op)).getAsJsonObject();
        assertNull(BookEdits.apply(book, op, ctx));
        assertNull(BookEdits.apply(client, wire, ctx));
        assertEquals(book.toJson(ctx), client.toJson(ctx), "server and client books must stay identical");
    }

    @Test
    void createKeepsFreeReadableIds() {
        JsonObject data = JsonUtil.parse("""
                {"id":"act2","title":"Act Two","autofocus":"act2/gate",
                 "quests":[{"id":"act2/gate","tasks":[{"id":"act2/gate_task","type":"checkmark"}]},
                           {"id":"act2/boss","dependencies":["act2/gate","a"],"tasks":[{"id":"act2/boss_task","type":"checkmark"}]}],
                 "links":[{"id":"act2/link","quest":"act2/gate"}]}
                """).getAsJsonObject();
        JsonObject op = BookEdits.create(ObjectKind.CHAPTER, "", 0, data);
        assertTrue(BookEdits.assignIds(book, op).isEmpty(), "nothing needed renaming");
        assertNull(BookEdits.apply(book, op, ctx));
        Chapter pasted = book.chapter("act2");
        assertNotNull(pasted);
        Quest gate = book.quest("act2/gate");
        assertEquals(List.of("act2/gate", "a"), book.quest("act2/boss").dependencies, "references to existing quests are kept");
        assertEquals("act2/gate", pasted.links.getFirst().quest);
        assertEquals("act2/gate", pasted.autofocus);
        assertSame(pasted, gate.chapter());
        assertTrue(book.dependents("act2/gate").contains(book.quest("act2/boss")));
        assertTrue(book.dependents("a").contains(book.quest("act2/boss")));
    }

    @Test
    void createRenamesTakenNewAndRepeatedIdsAndRemapsReferences() {
        // "a" and "a_task" are taken, "new" is the editor's placeholder, "dup" appears twice.
        JsonObject data = JsonUtil.parse("""
                {"id":"new","autofocus":"a",
                 "quests":[{"id":"a","tasks":[{"id":"a_task","type":"checkmark"}],"rewards":[{"id":"dup","type":"xp","xp":5}]},
                           {"id":"p2","dependencies":["a"],"tasks":[{"id":"dup","type":"checkmark"}]}],
                 "links":[{"id":"l1","quest":"a"}]}
                """).getAsJsonObject();
        JsonObject op = BookEdits.create(ObjectKind.CHAPTER, "", 0, data);
        Map<String, String> map = BookEdits.assignIds(book, op);
        assertEquals(java.util.Set.of("new", "a", "a_task", "dup"), map.keySet(), "only clashing ids are renamed: " + map);
        map.values().forEach(id -> assertFalse(book.isIdTaken(id)));
        assertNull(BookEdits.apply(book, op, ctx));
        Chapter pasted = book.chapters.getFirst();
        String newA = map.get("a");
        assertEquals(map.get("new"), pasted.id());
        assertNotNull(book.quest(newA));
        assertNotNull(book.quest("a"), "the original quest is untouched");
        assertEquals(List.of(newA), book.quest("p2").dependencies, "references follow the renamed pasted quest");
        assertEquals(newA, pasted.links.getFirst().quest);
        assertEquals(newA, pasted.autofocus);
        assertNotNull(book.get("l1", QuestLink.class));
        // The first "dup" keeps its id; the repeat gets a fresh one.
        assertNotNull(book.reward("dup"));
        assertNotNull(book.task(map.get("dup")));
        assertEquals(map.get("dup"), book.quest("p2").tasks.getFirst().id());
    }

    @Test
    void pastingTwiceNeverCollides() {
        JsonObject data = a.toJson(ctx);
        for (int i = 0; i < 3; i++) {
            applyBoth(BookEdits.create(ObjectKind.QUEST, "ch1", -1, data.deepCopy()));
        }
        assertEquals(5, ch1.quests.size());
        assertEquals(12 + 3 * 3, book.objectCount(), "each paste adds a quest, a task and a reward");
    }

    @Test
    void createTaskRewardAndTableEntry() {
        applyBoth(BookEdits.create(ObjectKind.TASK, "b", -1, JsonUtil.parse("{\"id\":\"\",\"type\":\"kill\",\"value\":4}").getAsJsonObject()));
        assertEquals(2, b.tasks.size());
        assertEquals(b, b.tasks.get(1).quest);
        applyBoth(BookEdits.create(ObjectKind.REWARD, "table", -1, JsonUtil.parse("{\"id\":\"\",\"type\":\"item\",\"item\":\"minecraft:gold_ingot\",\"weight\":3.5}").getAsJsonObject()));
        RewardTable table = book.table("table");
        assertEquals(1, table.entries.size());
        assertEquals(3.5, table.entries.getFirst().weight);
        assertSame(table, table.entries.getFirst().reward.table);
        applyBoth(BookEdits.create(ObjectKind.GROUP, "", 0, JsonUtil.parse("{\"id\":\"\",\"title\":\"G\"}").getAsJsonObject()));
        assertEquals("G", book.groups.getFirst().title);
    }

    @Test
    void createRefusesWrongParents() {
        JsonObject quest = JsonUtil.parse("{\"id\":\"zz\"}").getAsJsonObject();
        assertNotNull(BookEdits.apply(book, BookEdits.create(ObjectKind.QUEST, "a", -1, quest), ctx));
        assertNotNull(BookEdits.apply(book, BookEdits.create(ObjectKind.TASK, "ch1", -1, quest), ctx));
        assertNotNull(BookEdits.apply(book, BookEdits.create(ObjectKind.REWARD, "ch1", -1, quest), ctx));
        assertNotNull(BookEdits.apply(book, BookEdits.create(ObjectKind.QUEST, "ch1", -1, JsonUtil.parse("{\"id\":\"a\"}").getAsJsonObject()), ctx), "taken id");
        assertNotNull(BookEdits.apply(book, BookEdits.create(ObjectKind.BOOK, "", -1, quest), ctx));
        assertNotNull(BookEdits.apply(book, JsonUtil.parse("{\"op\":\"explode\"}").getAsJsonObject(), ctx));
        assertNotNull(BookEdits.apply(book, BookEdits.update("missing", new JsonObject()), ctx));
        assertNotNull(BookEdits.apply(book, BookEdits.delete("book"), ctx));
        assertEquals(2, ch1.quests.size(), "nothing changed");
    }

    @Test
    void updateReplacesPropsAndTableWeights() {
        JsonObject props = b.propsToJson(ctx);
        props.addProperty("title", "&6Renamed");
        props.addProperty("repeatable", true);
        applyBoth(BookEdits.update("b", props));
        assertEquals("&6Renamed", b.title);
        assertTrue(b.repeatable);
        assertEquals(List.of("a"), b.dependencies);
        // Props that are left out return to their defaults (the editor always sends the full set).
        JsonObject minimal = JsonUtil.parse("{\"id\":\"b\"}").getAsJsonObject();
        applyBoth(BookEdits.update("b", minimal));
        assertEquals("", b.title);
        assertTrue(b.dependencies.isEmpty());

        applyBoth(BookEdits.create(ObjectKind.REWARD, "table", -1, JsonUtil.parse("{\"id\":\"\",\"type\":\"xp\",\"weight\":1}").getAsJsonObject()));
        String entryId = book.table("table").entries.getFirst().reward.id();
        JsonObject entryProps = book.table("table").entries.getFirst().reward.propsToJson(ctx);
        entryProps.addProperty("weight", 9);
        applyBoth(BookEdits.update(entryId, entryProps));
        assertEquals(9, book.table("table").entries.getFirst().weight);
    }

    @Test
    void deletingAQuestCleansUpReferences() {
        applyBoth(BookEdits.delete("a"));
        assertNull(book.quest("a"));
        assertTrue(book.quest("b").dependencies.isEmpty());
        assertTrue(ch2.links.isEmpty(), "links to the deleted quest are removed");
        assertEquals("", ch2.autofocus);
        assertNull(book.task("a_task"));
        assertNull(book.reward("a_reward"));
    }

    @Test
    void deletingOtherObjects() {
        applyBoth(BookEdits.delete("grp"));
        assertTrue(book.groups.isEmpty());
        assertEquals("", book.chapter("ch2").group);
        applyBoth(BookEdits.delete("b_task"));
        assertTrue(book.quest("b").tasks.isEmpty());
        applyBoth(BookEdits.delete("a_reward"));
        assertTrue(book.quest("a").rewards.isEmpty());
        applyBoth(BookEdits.delete("img"));
        assertTrue(book.chapter("ch1").images.isEmpty());
        applyBoth(BookEdits.delete("link_to_a"));
        assertTrue(book.chapter("ch2").links.isEmpty());
        applyBoth(BookEdits.delete("table"));
        assertTrue(book.tables.isEmpty());
        applyBoth(BookEdits.delete("ch1"));
        assertEquals(1, book.chapters.size());
        assertNull(book.quest("a"));
        assertNull(book.task("a_task"));
    }

    @Test
    void moveShiftsPositions() {
        a.x = 1;
        a.y = 2;
        applyBoth(BookEdits.move(List.of("a", "link_to_a", "img", "ch1", "missing"), 1.5, -2));
        assertEquals(2.5, book.quest("a").x);
        assertEquals(0, book.quest("a").y);
        QuestLink link = book.get("link_to_a", QuestLink.class);
        assertEquals(1.5, link.x);
        assertEquals(-2, book.get("img", ChapterImage.class).y);
    }

    @Test
    void reorderMovesWithinParent() {
        applyBoth(BookEdits.reorder("ch2", 0));
        assertEquals(List.of("ch2", "ch1"), book.chapters.stream().map(QuestObject::id).toList());
        applyBoth(BookEdits.reorder("ch2", 99));
        assertEquals("ch2", book.chapters.getLast().id());
        CheckmarkTask extra = TestBooks.check(book.quest("b"), "b_task2");
        book.rebuildIndex();
        applyBoth(BookEdits.reorder("b_task2", 0));
        assertEquals("b_task2", book.quest("b").tasks.getFirst().id());
        assertNotNull(BookEdits.apply(book, BookEdits.reorder("img", 0), ctx), "images have no order");
        assertNotEquals(null, extra);
    }

    @Test
    void moveToChapter() {
        applyBoth(BookEdits.moveToChapter(List.of("b", "img"), "ch2"));
        Quest moved = book.quest("b");
        assertSame(book.chapter("ch2"), moved.chapter());
        assertTrue(book.chapter("ch2").quests.contains(moved));
        assertFalse(book.chapter("ch1").quests.contains(moved));
        assertSame(book.chapter("ch2"), book.get("img", ChapterImage.class).chapter());
        assertNotNull(BookEdits.apply(book, BookEdits.moveToChapter(List.of("a"), "nope"), ctx));
    }

    @Test
    void setBookReplacesEverything() {
        QuestBook other = new QuestBook();
        Chapter only = TestBooks.chapter(other, "only");
        TestBooks.check(TestBooks.quest(only, "q"), "t");
        other.title = "Other";
        other.rebuildIndex();
        applyBoth(BookEdits.setBook(other.toJson(ctx)));
        assertEquals("Other", book.title);
        assertEquals(1, book.chapters.size());
        assertNull(book.quest("a"));
        assertNotNull(book.task("t"));
    }

    @Test
    void itemRewardDefaultsSurviveEdits() {
        ItemReward reward = (ItemReward) book.reward("a_reward");
        JsonObject props = reward.propsToJson(ctx);
        props.addProperty("count", 12);
        applyBoth(BookEdits.update("a_reward", props));
        assertEquals(12, ((ItemReward) book.reward("a_reward")).count);
        assertTrue(((ItemReward) book.reward("a_reward")).item.is(net.minecraft.world.item.Items.EMERALD));
    }
}
