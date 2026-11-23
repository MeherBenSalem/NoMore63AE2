package dev.nightbeam.corerpg.quests.network;

import dev.nightbeam.corerpg.quests.client.ClientPacketHandler;
import dev.nightbeam.corerpg.quests.server.ServerPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import dev.nightbeam.corerpg.quests.network.compat.StreamCodec;
import dev.nightbeam.corerpg.quests.network.compat.CustomPacketPayload;
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
                                                           StreamCodec<FriendlyByteBuf, T> codec,
                                                           Consumer<T> handler) {
    }

    public record ToServer<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                           StreamCodec<FriendlyByteBuf, T> codec,
                                                           BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<ToClient<?>> TO_CLIENT = new ArrayList<>();
    private static final List<ToServer<?>> TO_SERVER = new ArrayList<>();
    /** Minecraft 1.20.1 edition: payloads travel as raw buffers on their own channel, so senders look the codec up here. */
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, StreamCodec<FriendlyByteBuf, ?>> CODECS = new java.util.HashMap<>();

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

    private static <T extends CustomPacketPayload> void toClient(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec, Consumer<T> handler) {
        TO_CLIENT.add(new ToClient<>(type, codec, handler));
        CODECS.put(type.id(), codec);
    }

    private static <T extends CustomPacketPayload> void toServer(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        TO_SERVER.add(new ToServer<>(type, codec, handler));
        CODECS.put(type.id(), codec);
    }

    /** Writes {@code payload} with the codec registered for its type (Minecraft 1.20.1 edition). */
    @SuppressWarnings("unchecked")
    public static FriendlyByteBuf encode(CustomPacketPayload payload) {
        StreamCodec<FriendlyByteBuf, CustomPacketPayload> codec = (StreamCodec<FriendlyByteBuf, CustomPacketPayload>) CODECS.get(payload.type().id());
        if (codec == null) {
            throw new IllegalArgumentException("Unregistered packet " + payload.type().id());
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        codec.encode(buf, payload);
        return buf;
    }

    /** Writes {@code payload} into {@code buf} with its registered codec (Minecraft 1.20.1 edition). */
    @SuppressWarnings("unchecked")
    public static void write(CustomPacketPayload payload, FriendlyByteBuf buf) {
        StreamCodec<FriendlyByteBuf, CustomPacketPayload> codec = (StreamCodec<FriendlyByteBuf, CustomPacketPayload>) CODECS.get(payload.type().id());
        if (codec == null) {
            throw new IllegalArgumentException("Unregistered packet " + payload.type().id());
        }
        codec.encode(buf, payload);
    }

    /** Reads a payload of the registered type {@code id} from {@code buf} (Minecraft 1.20.1 edition). */
    public static CustomPacketPayload read(net.minecraft.resources.ResourceLocation id, FriendlyByteBuf buf) {
        StreamCodec<FriendlyByteBuf, ?> codec = CODECS.get(id);
        if (codec == null) {
            throw new IllegalArgumentException("Unknown packet " + id);
        }
        return (CustomPacketPayload) codec.decode(buf);
    }
}
