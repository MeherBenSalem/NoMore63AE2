package dev.nightbeam.corerpg.quests.book;

import dev.nightbeam.corerpg.quests.McTest;
import dev.nightbeam.corerpg.quests.TestBooks;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.server.BookStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookStorageTest extends McTest {
    @Test
    void saveAndLoadRoundTrip(@TempDir Path dir) throws Exception {
        JsonCtx ctx = SchemaRoundTripTest.ctx();
        QuestBook book = SchemaRoundTripTest.fullBook();
        Chapter second = TestBooks.chapter(book, "act2");
        TestBooks.check(TestBooks.quest(second, "act2/q"), "act2/t");
        book.rebuildIndex();
        BookStorage.save(book, dir, ctx);

        assertTrue(Files.exists(dir.resolve("book.json")));
        assertTrue(Files.exists(dir.resolve("chapters/act1~start.json")), "'/' in ids becomes '~' in file names");
        assertTrue(Files.exists(dir.resolve("chapters/act2.json")));
        assertTrue(Files.exists(dir.resolve("reward_tables/table_a.json")));

        QuestBook loaded = BookStorage.load(dir, ctx);
        assertEquals(book.toJson(ctx), loaded.toJson(ctx));
    }

    @Test
    void chapterOrderIsKept(@TempDir Path dir) {
        JsonCtx ctx = SchemaRoundTripTest.ctx();
        QuestBook book = new QuestBook();
        for (String id : List.of("zeta", "alpha", "mid")) {
            TestBooks.chapter(book, id);
        }
        book.rebuildIndex();
        BookStorage.save(book, dir, ctx);
        assertEquals(List.of("zeta", "alpha", "mid"), BookStorage.load(dir, ctx).chapters.stream().map(QuestObject::id).toList());
    }

    @Test
    void chaptersMissingFromTheOrderAreAppended(@TempDir Path dir) throws Exception {
        JsonCtx ctx = SchemaRoundTripTest.ctx();
        QuestBook book = new QuestBook();
        TestBooks.chapter(book, "b");
        book.rebuildIndex();
        BookStorage.save(book, dir, ctx);
        // A chapter file added by hand (no id inside: it comes from the file name).
        Files.writeString(dir.resolve("chapters/hand~made.json"), "{\"title\":\"By hand\", \"quests\":[]}");
        QuestBook loaded = BookStorage.load(dir, ctx);
        assertEquals(List.of("b", "hand/made"), loaded.chapters.stream().map(QuestObject::id).toList());
        assertEquals("By hand", loaded.chapter("hand/made").title);
    }

    @Test
    void staleFilesAreDeleted(@TempDir Path dir) throws Exception {
        JsonCtx ctx = SchemaRoundTripTest.ctx();
        QuestBook book = new QuestBook();
        TestBooks.chapter(book, "keep");
        TestBooks.chapter(book, "gone");
        RewardTable t = new RewardTable(book);
        t.setIdInternal("old_table");
        book.tables.add(t);
        book.rebuildIndex();
        BookStorage.save(book, dir, ctx);
        assertTrue(Files.exists(dir.resolve("chapters/gone.json")));
        book.chapters.removeIf(c -> c.id().equals("gone"));
        book.tables.clear();
        book.rebuildIndex();
        Files.writeString(dir.resolve("chapters/notes.txt"), "not a chapter");
        BookStorage.save(book, dir, ctx);
        assertFalse(Files.exists(dir.resolve("chapters/gone.json")));
        assertFalse(Files.exists(dir.resolve("reward_tables/old_table.json")));
        assertTrue(Files.exists(dir.resolve("chapters/keep.json")));
        assertTrue(Files.exists(dir.resolve("chapters/notes.txt")), "only .json files are managed");
    }

    @Test
    void brokenChapterFileDoesNotBreakTheBook(@TempDir Path dir) throws Exception {
        JsonCtx ctx = SchemaRoundTripTest.ctx();
        QuestBook book = new QuestBook();
        TestBooks.check(TestBooks.quest(TestBooks.chapter(book, "good"), "q"), "t");
        book.rebuildIndex();
        BookStorage.save(book, dir, ctx);
        Files.writeString(dir.resolve("chapters/broken.json"), "{ this is not json ");
        QuestBook loaded = BookStorage.load(dir, ctx);
        assertNotNull(loaded.quest("q"));
        assertEquals(1, loaded.chapters.size());
    }

    @Test
    void firstStartWritesABook(@TempDir Path dir) {
        QuestBook book = BookStorage.load(dir, SchemaRoundTripTest.ctx());
        assertNotNull(book);
        assertTrue(Files.exists(dir.resolve("book.json")));
    }

    @Test
    void singleFileExport(@TempDir Path dir) throws Exception {
        JsonCtx ctx = SchemaRoundTripTest.ctx();
        QuestBook book = SchemaRoundTripTest.fullBook();
        Path file = dir.resolve("export/book.json");
        BookStorage.exportSingleFile(book, file, ctx);
        QuestBook back = new QuestBook();
        back.fromJson(dev.nightbeam.corerpg.quests.util.JsonUtil.readObject(file), ctx);
        assertEquals(book.toJson(ctx), back.toJson(ctx));
    }
}
