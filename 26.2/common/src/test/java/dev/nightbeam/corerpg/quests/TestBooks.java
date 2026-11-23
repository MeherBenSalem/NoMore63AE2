package dev.nightbeam.corerpg.quests;

import dev.nightbeam.corerpg.quests.book.Chapter;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.reward.types.ItemReward;
import dev.nightbeam.corerpg.quests.task.types.CheckmarkTask;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Small builders for books in tests. Call {@link QuestBook#rebuildIndex()} when done. */
public final class TestBooks {
    private TestBooks() {
    }

    public static Chapter chapter(QuestBook book, String id) {
        Chapter c = new Chapter(book);
        c.setIdInternal(id);
        book.chapters.add(c);
        return c;
    }

    public static Quest quest(Chapter chapter, String id, String... deps) {
        Quest q = new Quest(chapter);
        q.setIdInternal(id);
        q.dependencies = new java.util.ArrayList<>(List.of(deps));
        chapter.quests.add(q);
        return q;
    }

    public static CheckmarkTask check(Quest quest, String id) {
        CheckmarkTask t = new CheckmarkTask();
        t.setIdInternal(id);
        t.quest = quest;
        quest.tasks.add(t);
        return t;
    }

    public static ItemReward itemReward(Quest quest, String id, int count) {
        ItemReward r = new ItemReward();
        r.setIdInternal(id);
        r.item = new ItemStack(Items.EMERALD);
        r.count = count;
        r.quest = quest;
        quest.rewards.add(r);
        return r;
    }
}
