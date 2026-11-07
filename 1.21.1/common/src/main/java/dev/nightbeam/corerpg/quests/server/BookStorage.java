package dev.nightbeam.corerpg.quests.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.ChapterGroup;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.book.RewardTable;
import dev.nightbeam.corerpg.quests.schema.JsonCtx;
import dev.nightbeam.corerpg.quests.util.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The quest book on disk, made to be friendly to version control and to pack tooling:
 * <pre>
 *   book/book.json               settings, chapter groups and chapter order
 *   book/chapters/&lt;id&gt;.json      one file per chapter, with its quests, links and images
 *   book/reward_tables/&lt;id&gt;.json one file per reward table
 * </pre>
 */
public final class BookStorage {
    private BookStorage() {
    }

    public static Path bookDir(Path configDir) {
        return configDir.resolve("book");
    }

    /** Loads the book, creating the example book on first start. */
    public static QuestBook load(Path dir, JsonCtx ctx) {
        QuestBook book = new QuestBook();
        Path bookFile = dir.resolve("book.json");
        if (!Files.exists(bookFile)) {
            CoreQuests.LOG.info("No quest book found in {}; writing the example book", dir);
            JsonObject example = exampleBook();
            if (example != null) {
                book.fromJson(example, ctx);
            } else {
                book.rebuildIndex();
            }
            save(book, dir, ctx);
            return book;
        }
        try {
            JsonObject json = JsonUtil.readObject(bookFile);
            book.readProps(json, ctx);
            if (json.has("groups")) {
                for (JsonElement e : json.getAsJsonArray("groups")) {
                    ChapterGroup group = new ChapterGroup(book);
                    group.setIdInternal(JsonUtil.getString(e.getAsJsonObject(), "id", ""));
                    group.fromJson(e.getAsJsonObject(), ctx);
                    book.groups.add(group);
                }
            }
            Map<String, Chapter> chapters = new LinkedHashMap<>();
            for (Path file : jsonFiles(dir.resolve("chapters"))) {
                try {
                    Chapter chapter = Chapter.read(book, JsonUtil.readObject(file), ctx);
                    if (chapter.id().isEmpty()) {
                        chapter.setIdInternal(file.getFileName().toString().replace(".json", "").replace('~', '/'));
                    }
                    chapters.put(chapter.id(), chapter);
                } catch (IOException | RuntimeException e) {
                    CoreQuests.LOG.error("Could not read chapter file {}: {}", file, e.getMessage());
                }
            }
            if (json.has("chapter_order")) {
                for (JsonElement e : json.getAsJsonArray("chapter_order")) {
                    Chapter chapter = chapters.remove(e.getAsString());
                    if (chapter != null) {
                        book.chapters.add(chapter);
                    }
                }
            }
            book.chapters.addAll(chapters.values());
            for (Path file : jsonFiles(dir.resolve("reward_tables"))) {
                try {
                    book.tables.add(RewardTable.read(book, JsonUtil.readObject(file), ctx));
                } catch (IOException | RuntimeException e) {
                    CoreQuests.LOG.error("Could not read reward table file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            CoreQuests.LOG.error("Could not read the quest book from {}", dir, e);
        }
        book.rebuildIndex();
        CoreQuests.LOG.info("Loaded the quest book: {} chapters, {} quests, {} reward tables",
                book.chapters.size(), book.allQuests().size(), book.tables.size());
        return book;
    }

    public static void save(QuestBook book, Path dir, JsonCtx ctx) {
        try {
            JsonObject json = book.propsToJson(ctx);
            json.remove("id");
            JsonArray groups = new JsonArray();
            book.groups.forEach(g -> groups.add(g.toJson(ctx)));
            json.add("groups", groups);
            JsonArray order = new JsonArray();
            book.chapters.forEach(c -> order.add(c.id()));
            json.add("chapter_order", order);
            JsonUtil.write(dir.resolve("book.json"), json);

            Set<Path> written = new HashSet<>();
            Path chapterDir = dir.resolve("chapters");
            for (Chapter chapter : book.chapters) {
                Path file = chapterDir.resolve(fileName(chapter.id()));
                JsonUtil.write(file, chapter.toJson(ctx));
                written.add(file);
            }
            deleteStale(chapterDir, written);

            written.clear();
            Path tableDir = dir.resolve("reward_tables");
            for (RewardTable table : book.tables) {
                Path file = tableDir.resolve(fileName(table.id()));
                JsonUtil.write(file, table.toJson(ctx));
                written.add(file);
            }
            deleteStale(tableDir, written);
        } catch (IOException e) {
            CoreQuests.LOG.error("Could not save the quest book to {}", dir, e);
        }
    }

    /** The whole book as one JSON file (for /corequests export and pack tools). */
    public static void exportSingleFile(QuestBook book, Path file, JsonCtx ctx) throws IOException {
        JsonUtil.write(file, book.toJson(ctx));
    }

    static String fileName(String id) {
        return id.replace('/', '~') + ".json";
    }

    private static List<Path> jsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return new ArrayList<>(stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList());
        }
    }

    private static void deleteStale(Path dir, Set<Path> keep) throws IOException {
        for (Path file : jsonFiles(dir)) {
            if (!keep.contains(file)) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static JsonObject exampleBook() {
        try (InputStream in = BookStorage.class.getResourceAsStream("/corerpg_quests/example_book.json")) {
            if (in == null) {
                return null;
            }
            return JsonUtil.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            CoreQuests.LOG.error("Could not read the bundled example book", e);
            return null;
        }
    }
}
