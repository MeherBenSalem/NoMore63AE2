package dev.nightbeam.corerpg.quests.book;

import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;

/** A heading in the chapter list that chapters can be filed under. */
public final class ChapterGroup extends QuestObject {
    QuestBook book;
    public boolean collapsedByDefault = false;

    public ChapterGroup(QuestBook book) {
        this.book = book;
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.GROUP;
    }

    @Override
    public QuestBook book() {
        return book;
    }

    @Override
    protected void defineProps(Props p) {
        super.defineProps(p);
        p.bool("collapsed_by_default", false, () -> collapsedByDefault, v -> collapsedByDefault = v);
    }

    @Override
    protected Component defaultTitle() {
        return Component.translatable("corerpg_quests.group");
    }
}
