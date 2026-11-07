package dev.nightbeam.corerpg.quests.client;

import com.google.gson.JsonObject;
import dev.nightbeam.corerpg.quests.CoreQuests;
import dev.nightbeam.corerpg.quests.book.BookEdits;
import dev.nightbeam.corerpg.quests.book.Quest;
import dev.nightbeam.corerpg.quests.book.QuestBook;
import dev.nightbeam.corerpg.quests.client.hud.QuestToast;
import dev.nightbeam.corerpg.quests.client.hud.RewardsToast;
import dev.nightbeam.corerpg.quests.client.screen.QuestBookScreen;
import dev.nightbeam.corerpg.quests.config.CoreConfig;
import dev.nightbeam.corerpg.quests.network.Payloads;
import dev.nightbeam.corerpg.quests.platform.Services;
import dev.nightbeam.corerpg.quests.progress.QuestLogic;
import dev.nightbeam.corerpg.quests.progress.TeamProgress;
import dev.nightbeam.corerpg.quests.util.JsonUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.HashSet;
import java.util.Set;

/** Applies packets from the server to the client state. Runs on the client thread. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void syncBook(Payloads.SyncBook p) {
        try {
            JsonObject json = JsonUtil.parse(JsonUtil.gunzip(p.data())).getAsJsonObject();
            QuestBook book = new QuestBook();
            book.fromJson(json, ClientQuests.ctx());
            ClientQuests.setBook(book);
        } catch (RuntimeException e) {
            CoreQuests.LOG.error("Could not read the quest book sent by the server", e);
        }
    }

    public static void syncState(Payloads.SyncState p) {
        try {
            Set<String> startedBefore = startedQuests();
            ClientQuests.setState(JsonUtil.parse(JsonUtil.gunzip(p.data())).getAsJsonObject());
            autoPin(startedBefore);
        } catch (RuntimeException e) {
            CoreQuests.LOG.error("Could not read the quest progress sent by the server", e);
        }
    }

    public static void bookEdit(Payloads.BookEdit p) {
        QuestBook book = ClientQuests.book();
        if (book == null) {
            return;
        }
        JsonObject op = JsonUtil.parse(JsonUtil.gunzip(p.data())).getAsJsonObject();
        String error = BookEdits.apply(book, op, ClientQuests.ctx());
        if (error != null) {
            CoreQuests.LOG.warn("Could not apply a book edit from the server: {}", error);
        }
        book.version++;
        if ("create".equals(JsonUtil.getString(op, "op", "")) && op.has("data")) {
            ClientQuests.setLastCreated(JsonUtil.getString(op.getAsJsonObject("data"), "id", ""));
        }
        ClientQuests.changed();
    }

    public static void editorData(Payloads.EditorData p) {
        ClientQuests.setEditorData(JsonUtil.parse(JsonUtil.gunzip(p.data())).getAsJsonObject());
    }

    public static void syncConfig(Payloads.SyncConfig p) {
        if (Minecraft.getInstance().getSingleplayerServer() == null) {
            CoreConfig.SERVER.spec.fromJson(JsonUtil.parse(p.json()).getAsJsonObject());
        }
    }

    public static void notify(Payloads.Notify p) {
        Minecraft mc = Minecraft.getInstance();
        Payloads.NotifyKind kind = p.notifyKind();
        if (CoreConfig.CLIENT.toasts.get() || kind == Payloads.NotifyKind.TOAST) {
            mc.getToasts().addToast(new QuestToast(kind, p.title(), p.text(), p.icon()));
        }
        if (CoreConfig.CLIENT.sounds.get() && mc.player != null) {
            switch (kind) {
                case QUEST_COMPLETED -> mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.6F, 1.2F);
                case CHAPTER_COMPLETED, BOOK_COMPLETED -> mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
                case TASK_COMPLETED -> mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
                default -> {
                }
            }
        }
    }

    public static void rewardsReceived(Payloads.RewardsReceived p) {
        if (CoreConfig.CLIENT.toasts.get()) {
            Minecraft.getInstance().getToasts().addToast(new RewardsToast(p.items(), p.lines()));
        }
    }

    public static void openBook(Payloads.OpenBook p) {
        QuestBookScreen.open(p.focus());
    }

    private static Set<String> startedQuests() {
        Set<String> set = new HashSet<>();
        QuestBook book = ClientQuests.book();
        if (book == null || !ClientQuests.ready()) {
            return set;
        }
        QuestLogic logic = ClientQuests.logic();
        for (Quest q : book.allQuests()) {
            if (logic.isStarted(q)) {
                set.add(q.id());
            }
        }
        return set;
    }

    private static void autoPin(Set<String> startedBefore) {
        QuestBook book = ClientQuests.book();
        if (!CoreConfig.CLIENT.autoPin.get() || book == null || startedBefore.isEmpty() && !ClientQuests.ready()) {
            return;
        }
        QuestLogic logic = ClientQuests.logic();
        TeamProgress progress = ClientQuests.progress();
        for (Quest q : book.allQuests()) {
            if (!startedBefore.contains(q.id()) && logic.isStarted(q) && !logic.isCompleted(q) && !progress.isPinned(ClientQuests.self(), q.id())) {
                Services.PLATFORM.sendToServer(new Payloads.PinQuest(q.id(), true));
            }
        }
    }
}
