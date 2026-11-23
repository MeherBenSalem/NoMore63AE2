package dev.nightbeam.corerpg.quests.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A minimal loader-neutral event: listeners register, the mod calls {@link #invoker()}. */
public final class Event<T> {
    private final Function<List<T>, T> combiner;
    private final List<T> listeners = new ArrayList<>();
    private T invoker;

    private Event(Function<List<T>, T> combiner) {
        this.combiner = combiner;
        this.invoker = combiner.apply(List.of());
    }

    public static <T> Event<T> create(Function<List<T>, T> combiner) {
        return new Event<>(combiner);
    }

    public synchronized void register(T listener) {
        listeners.add(listener);
        invoker = combiner.apply(List.copyOf(listeners));
    }

    public T invoker() {
        return invoker;
    }
}
