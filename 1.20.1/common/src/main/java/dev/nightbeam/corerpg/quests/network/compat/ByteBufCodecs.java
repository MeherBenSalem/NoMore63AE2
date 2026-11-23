package dev.nightbeam.corerpg.quests.network.compat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Minecraft 1.20.1 edition of the 1.20.5+ codecs this mod's packets use, all on {@link FriendlyByteBuf}. */
public final class ByteBufCodecs {
    public static final StreamCodec<FriendlyByteBuf, Integer> VAR_INT = StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt);
    public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL = StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);
    public static final StreamCodec<FriendlyByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    /** Components travel as JSON, like vanilla 1.20.1 chat packets. */
    public static final StreamCodec<FriendlyByteBuf, Component> COMPONENT = StreamCodec.of(FriendlyByteBuf::writeComponent, FriendlyByteBuf::readComponent);
    public static final StreamCodec<FriendlyByteBuf, ItemStack> ITEM_STACK = StreamCodec.of((buf, stack) -> buf.writeItem(stack), FriendlyByteBuf::readItem);
    public static final StreamCodec<FriendlyByteBuf, List<ItemStack>> ITEM_STACK_LIST = list(ITEM_STACK, 4096);

    private ByteBufCodecs() {
    }

    public static StreamCodec<FriendlyByteBuf, String> stringUtf8(int maxLength) {
        return StreamCodec.of((buf, s) -> buf.writeUtf(s, maxLength), buf -> buf.readUtf(maxLength));
    }

    public static StreamCodec<FriendlyByteBuf, byte[]> byteArray(int maxLength) {
        return StreamCodec.of(FriendlyByteBuf::writeByteArray, buf -> buf.readByteArray(maxLength));
    }

    public static <V> StreamCodec<FriendlyByteBuf, List<V>> list(StreamCodec<FriendlyByteBuf, V> element, int maxSize) {
        return StreamCodec.of((buf, values) -> {
            buf.writeVarInt(values.size());
            for (V value : values) {
                element.encode(buf, value);
            }
        }, buf -> {
            int size = buf.readVarInt();
            if (size < 0 || size > maxSize) {
                throw new IllegalArgumentException("List of " + size + " elements is over the limit of " + maxSize);
            }
            List<V> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                out.add(element.decode(buf));
            }
            return out;
        });
    }

    public static <V> Function<StreamCodec<FriendlyByteBuf, V>, StreamCodec<FriendlyByteBuf, List<V>>> list(int maxSize) {
        return element -> list(element, maxSize);
    }
}
