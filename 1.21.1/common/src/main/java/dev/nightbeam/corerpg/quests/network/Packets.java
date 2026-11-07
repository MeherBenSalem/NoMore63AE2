package dev.nightbeam.corerpg.quests.network;

import dev.nightbeam.corerpg.quests.client.ClientPacketHandler;
import dev.nightbeam.corerpg.quests.server.ServerPacketHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The packet list both loaders register. Client handlers are lambdas (not method references) so the client classes
 * they call are only loaded when a packet actually arrives on a client.
 */
public final class Packets {
    public record ToClient<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                           StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                           Consumer<T> handler) {
    }

    public record ToServer<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                           StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                           BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<ToClient<?>> TO_CLIENT = new ArrayList<>();
    private static final List<ToServer<?>> TO_SERVER = new ArrayList<>();

    private Packets() {
    }

    public static List<ToClient<?>> toClient() {
        return Collections.unmodifiableList(TO_CLIENT);
    }

    public static List<ToServer<?>> toServer() {
        return Collections.unmodifiableList(TO_SERVER);
    }

    public static void bootstrap() {
        if (!TO_CLIENT.isEmpty()) {
            return;
        }
        toClient(Payloads.SyncBook.TYPE, Payloads.SyncBook.CODEC, p -> ClientPacketHandler.syncBook(p));
        toClient(Payloads.SyncState.TYPE, Payloads.SyncState.CODEC, p -> ClientPacketHandler.syncState(p));
        toClient(Payloads.BookEdit.TYPE, Payloads.BookEdit.CODEC, p -> ClientPacketHandler.bookEdit(p));
        toClient(Payloads.EditorData.TYPE, Payloads.EditorData.CODEC, p -> ClientPacketHandler.editorData(p));
        toClient(Payloads.SyncConfig.TYPE, Payloads.SyncConfig.CODEC, p -> ClientPacketHandler.syncConfig(p));
        toClient(Payloads.Notify.TYPE, Payloads.Notify.CODEC, p -> ClientPacketHandler.notify(p));
        toClient(Payloads.RewardsReceived.TYPE, Payloads.RewardsReceived.CODEC, p -> ClientPacketHandler.rewardsReceived(p));
        toClient(Payloads.OpenBook.TYPE, Payloads.OpenBook.CODEC, p -> ClientPacketHandler.openBook(p));

        toServer(Payloads.ClaimReward.TYPE, Payloads.ClaimReward.CODEC, ServerPacketHandler::claimReward);
        toServer(Payloads.ClaimAll.TYPE, Payloads.ClaimAll.CODEC, ServerPacketHandler::claimAll);
        toServer(Payloads.SubmitTask.TYPE, Payloads.SubmitTask.CODEC, ServerPacketHandler::submitTask);
        toServer(Payloads.PinQuest.TYPE, Payloads.PinQuest.CODEC, ServerPacketHandler::pinQuest);
        toServer(Payloads.RequestEdit.TYPE, Payloads.RequestEdit.CODEC, ServerPacketHandler::requestEdit);
        toServer(Payloads.ChangeProgress.TYPE, Payloads.ChangeProgress.CODEC, ServerPacketHandler::changeProgress);
        toServer(Payloads.SetEditMode.TYPE, Payloads.SetEditMode.CODEC, ServerPacketHandler::setEditMode);
        toServer(Payloads.TeamAction.TYPE, Payloads.TeamAction.CODEC, ServerPacketHandler::teamAction);
        toServer(Payloads.SetFilter.TYPE, Payloads.SetFilter.CODEC, ServerPacketHandler::setFilter);
        toServer(Payloads.VeinState.TYPE, Payloads.VeinState.CODEC, ServerPacketHandler::veinState);
        toServer(Payloads.EmergencyItems.TYPE, Payloads.EmergencyItems.CODEC, ServerPacketHandler::emergencyItems);
    }

    private static <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        TO_CLIENT.add(new ToClient<>(type, codec, handler));
    }

    private static <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        TO_SERVER.add(new ToServer<>(type, codec, handler));
    }
}
