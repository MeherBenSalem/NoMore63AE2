package dev.nightbeam.corerpg.quests.book;

import dev.nightbeam.corerpg.quests.schema.Props;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Shows a quest from another chapter on this chapter's canvas; clicking it opens the real quest. */
public final class QuestLink extends QuestObject {
    Chapter chapter;
    public String quest = "";
    public double x;
    public double y;
    public QuestShape shape = QuestShape.DEFAULT;
    public double size = 0;

    public QuestLink(Chapter chapter) {
        this.chapter = chapter;
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.LINK;
    }

    @Override
    public QuestBook book() {
        return chapter == null ? null : chapter.book();
    }

    public Chapter chapter() {
        return chapter;
    }

    public @Nullable Quest target() {
        QuestBook book = book();
        return book == null ? null : book.quest(quest);
    }

    @Override
    protected Component defaultTitle() {
        Quest target = target();
        return target == null ? Component.translatable("corerpg_quests.link.broken") : target.displayTitle();
    }

    @Override
    protected Icon defaultIcon() {
        Quest target = target();
        return target == null ? Icon.EMPTY : target.displayIcon();
    }

    @Override
    protected void defineProps(Props p) {
        p.category("general");
        p.id("quest", () -> quest, v -> quest = v);
        p.category("appearance");
        p.decimal("x", 0, -100000, 100000, () -> x, v -> x = v);
        p.decimal("y", 0, -100000, 100000, () -> y, v -> y = v);
        p.enumValue("shape", QuestShape.class, QuestShape.DEFAULT, () -> shape, v -> shape = v);
        p.decimal("size", 0, 0, 8, () -> size, v -> size = v);
    }

    public QuestShape effectiveShape() {
        if (shape != QuestShape.DEFAULT) {
            return shape;
        }
        Quest target = target();
        return target != null ? target.effectiveShape() : chapter.effectiveShape();
    }

    public double effectiveSize() {
        if (size > 0) {
            return size;
        }
        Quest target = target();
        return target != null ? target.effectiveSize() : chapter.effectiveQuestSize();
    }
}
