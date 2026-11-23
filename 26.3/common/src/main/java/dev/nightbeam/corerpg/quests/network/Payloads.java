package dev.nightbeam.corerpg.quests.network;

import dev.nightbeam.corerpg.quests.CoreQuests;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Every packet. Large or structured data (the book, progress, edits) travels as gzipped JSON produced by the same code
 * that writes the files, so there is one serialisation path to get right.
 */
public final class Payloads {
    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(CoreQuests.id(name));
    }

    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(8 * 1024 * 1024);

    // ---------------- Server -> client ----------------

    /** The whole quest book (gzipped JSON). */
    public record SyncBook(byte[] data) implements CustomPacketPayload {
        public static final Type<SyncBook> TYPE = payloadType("sync_book");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncBook> CODEC = StreamCodec.composite(BYTES, SyncBook::data, SyncBook::new);

        @Override
        public Type<SyncBook> type() {
            return TYPE;
        }
    }

    /** The player's team, its progress and session flags (gzipped JSON). */
    public record SyncState(byte[] data) implements CustomPacketPayload {
        public static final Type<SyncState> TYPE = payloadType("sync_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncState> CODEC = StreamCodec.composite(BYTES, SyncState::data, SyncState::new);

        @Override
        public Type<SyncState> type() {
            return TYPE;
        }
    }

    /** One applied book edit, replayed on every client (gzipped JSON). */
    public record BookEdit(byte[] data) implements CustomPacketPayload {
        public static final Type<BookEdit> TYPE = payloadType("book_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, BookEdit> CODEC = StreamCodec.composite(BYTES, BookEdit::data, BookEdit::new);

        @Override
        public Type<BookEdit> type() {
            return TYPE;
        }
    }

    /** Ids the editor offers as suggestions (advancements, structures, loot tables...) (gzipped JSON). */
    public record EditorData(byte[] data) implements CustomPacketPayload {
        public static final Type<EditorData> TYPE = payloadType("editor_data");
        public static final StreamCodec<RegistryFriendlyByteBuf, EditorData> CODEC = StreamCodec.composite(BYTES, EditorData::data, EditorData::new);

        @Override
        public Type<EditorData> type() {
            return TYPE;
        }
    }

    /** Server settings the client needs (vein mining limits). JSON text. */
    public record SyncConfig(String json) implements CustomPacketPayload {
        public static final Type<SyncConfig> TYPE = payloadType("sync_config");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncConfig> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(262144), SyncConfig::json, SyncConfig::new);

        @Override
        public Type<SyncConfig> type() {
            return TYPE;
        }
    }

    public enum NotifyKind {
        QUEST_COMPLETED, CHAPTER_COMPLETED, BOOK_COMPLETED, TASK_COMPLETED, QUEST_UNLOCKED, TOAST
    }

    /** A toast: quest or chapter completed, quest unlocked, or a custom toast reward. */
    public record Notify(int kind, String objectId, Component title, Component text, ItemStack icon) implements CustomPacketPayload {
        public static final Type<Notify> TYPE = payloadType("notify");
        public static final StreamCodec<RegistryFriendlyByteBuf, Notify> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Notify::kind,
                ByteBufCodecs.STRING_UTF8, Notify::objectId,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Notify::title,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Notify::text,
                ItemStack.OPTIONAL_STREAM_CODEC, Notify::icon,
                Notify::new);

        public NotifyKind notifyKind() {
            NotifyKind[] values = NotifyKind.values();
            return kind >= 0 && kind < values.length ? values[kind] : NotifyKind.TOAST;
        }

        @Override
        public Type<Notify> type() {
            return TYPE;
        }
    }

    /** What a claim gave: item stacks and text lines (XP, stages...). */
    public record RewardsReceived(List<ItemStack> items, List<Component> lines) implements CustomPacketPayload {
        public static final Type<RewardsReceived> TYPE = payloadType("rewards_received");
        public static final StreamCodec<RegistryFriendlyByteBuf, RewardsReceived> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, RewardsReceived::items,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(64)), RewardsReceived::lines,
                RewardsReceived::new);

        @Override
        public Type<RewardsReceived> type() {
            return TYPE;
        }
    }

    /** Opens the quest book, optionally at a quest or chapter. */
    public record OpenBook(String focus) implements CustomPacketPayload {
        public static final Type<OpenBook> TYPE = payloadType("open_book");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenBook> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, OpenBook::focus, OpenBook::new);

        @Override
        public Type<OpenBook> type() {
            return TYPE;
        }
    }

    // ---------------- Client -> server ----------------

    public record ClaimReward(String rewardId, int choice) implements CustomPacketPayload {
        public static final Type<ClaimReward> TYPE = payloadType("claim_reward");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimReward> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ClaimReward::rewardId, ByteBufCodecs.VAR_INT, ClaimReward::choice, ClaimReward::new);

        @Override
        public Type<ClaimReward> type() {
            return TYPE;
        }
    }

    /** Claims every claimable reward of a quest, a chapter, or (empty id) the whole book. */
    public record ClaimAll(String scopeId) implements CustomPacketPayload {
        public static final Type<ClaimAll> TYPE = payloadType("claim_all");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClaimAll> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClaimAll::scopeId, ClaimAll::new);

        @Override
        public Type<ClaimAll> type() {
            return TYPE;
        }
    }

    public record SubmitTask(String taskId) implements CustomPacketPayload {
        public static final Type<SubmitTask> TYPE = payloadType("submit_task");
        public static final StreamCodec<RegistryFriendlyByteBuf, SubmitTask> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SubmitTask::taskId, SubmitTask::new);

        @Override
        public Type<SubmitTask> type() {
            return TYPE;
        }
    }

    public record PinQuest(String questId, boolean pin) implements CustomPacketPayload {
        public static final Type<PinQuest> TYPE = payloadType("pin_quest");
        public static final StreamCodec<RegistryFriendlyByteBuf, PinQuest> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PinQuest::questId, ByteBufCodecs.BOOL, PinQuest::pin, PinQuest::new);

        @Override
        public Type<PinQuest> type() {
            return TYPE;
        }
    }

    /** An edit requested by an editor (gzipped JSON); the server validates, applies and broadcasts it. */
    public record RequestEdit(byte[] data) implements CustomPacketPayload {
        public static final Type<RequestEdit> TYPE = payloadType("request_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestEdit> CODEC = StreamCodec.composite(ByteBufCodecs.byteArray(30000), RequestEdit::data, RequestEdit::new);

        @Override
        public Type<RequestEdit> type() {
            return TYPE;
        }
    }

    /** Editor: complete or reset an object (quest, chapter, book) for the editor's team. */
    public record ChangeProgress(String objectId, boolean complete) implements CustomPacketPayload {
        public static final Type<ChangeProgress> TYPE = payloadType("change_progress");
        public static final StreamCodec<RegistryFriendlyByteBuf, ChangeProgress> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ChangeProgress::objectId, ByteBufCodecs.BOOL, ChangeProgress::complete, ChangeProgress::new);

        @Override
        public Type<ChangeProgress> type() {
            return TYPE;
        }
    }

    public record SetEditMode(boolean editing) implements CustomPacketPayload {
        public static final Type<SetEditMode> TYPE = payloadType("set_edit_mode");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetEditMode> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, SetEditMode::editing, SetEditMode::new);

        @Override
        public Type<SetEditMode> type() {
            return TYPE;
        }
    }

    /** A team action from the team screen; runs the same code as the /team command. */
    public record TeamAction(String action, String arg) implements CustomPacketPayload {
        public static final Type<TeamAction> TYPE = payloadType("team_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, TeamAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TeamAction::action, ByteBufCodecs.STRING_UTF8, TeamAction::arg, TeamAction::new);

        @Override
        public Type<TeamAction> type() {
            return TYPE;
        }
    }

    /** Saves a filter expression onto the smart filter held in a hand. */
    public record SetFilter(boolean mainHand, String filter) implements CustomPacketPayload {
        public static final Type<SetFilter> TYPE = payloadType("set_filter");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetFilter> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SetFilter::mainHand, ByteBufCodecs.stringUtf8(8192), SetFilter::filter, SetFilter::new);

        @Override
        public Type<SetFilter> type() {
            return TYPE;
        }
    }

    /** The vein mining key state and selected shape. */
    public record VeinState(boolean active, int shape) implements CustomPacketPayload {
        public static final Type<VeinState> TYPE = payloadType("vein_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, VeinState> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, VeinState::active, ByteBufCodecs.VAR_INT, VeinState::shape, VeinState::new);

        @Override
        public Type<VeinState> type() {
            return TYPE;
        }
    }

    /** Asks for the emergency items (book settings) after losing the starting kit. */
    public record EmergencyItems() implements CustomPacketPayload {
        public static final Type<EmergencyItems> TYPE = payloadType("emergency_items");
        public static final StreamCodec<RegistryFriendlyByteBuf, EmergencyItems> CODEC = StreamCodec.unit(new EmergencyItems());

        @Override
        public Type<EmergencyItems> type() {
            return TYPE;
        }
    }
}
