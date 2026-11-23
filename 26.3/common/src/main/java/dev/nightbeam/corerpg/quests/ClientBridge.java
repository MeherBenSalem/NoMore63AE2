package dev.nightbeam.corerpg.quests;

import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.server.ServerQuests;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Hooks the client module fills in, so common code (items, packets) can reach client-only code without loading it
 * on a dedicated server.
 */
public final class ClientBridge {
    public static Runnable openQuestBook = () -> {
    };
    public static Consumer<InteractionHand> openFilterEditor = hand -> {
    };
    public static Supplier<@Nullable QuestBook> clientBook = () -> null;

    private ClientBridge() {
    }

    public static void openQuestBook() {
        openQuestBook.run();
    }

    public static void openFilterEditor(InteractionHand hand) {
        openFilterEditor.accept(hand);
    }

    /** The book for display: the client's synced copy when there is one, otherwise the server's. */
    public static @Nullable QuestBook anyBook() {
        QuestBook book = clientBook.get();
        if (book != null) {
            return book;
        }
        ServerQuests server = ServerQuests.getOrNull();
        return server == null ? null : server.book();
    }
}
