package dev.nightbeam.corerpg.quests.client;

import dev.nightbeam.corerpg.quests.ClientBridge;
import dev.nightbeam.corerpg.quests.client.screen.FilterEditorScreen;
import dev.nightbeam.corerpg.quests.client.screen.QuestBookScreen;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import net.minecraft.client.Minecraft;

/** Client setup shared by both loaders. */
public final class CoreQuestsClient {
    private CoreQuestsClient() {
    }

    public static void init() {
        CoreConfig.loadClient();
        ClientBridge.openQuestBook = () -> QuestBookScreen.open("");
        ClientBridge.openFilterEditor = hand -> Minecraft.getInstance().setScreen(new FilterEditorScreen(hand));
        ClientBridge.clientBook = ClientQuests::book;
    }
}
