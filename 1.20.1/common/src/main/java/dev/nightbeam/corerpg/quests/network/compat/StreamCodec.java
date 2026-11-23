package dev.nightbeam.corerpg.quests.network.compat;

import java.util.function.Function;

/** Minecraft 1.20.1 edition of the 1.20.5+ {@code StreamCodec}: reads and writes one value on a buffer. */
public interface StreamCodec<B, V> {
    V decode(B buf);

    void encode(B buf, V value);

    default <O> O apply(Function<StreamCodec<B, V>, O> operator) {
        return operator.apply(this);
    }

    static <B, V> StreamCodec<B, V> of(Encoder<B, V> encoder, Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return decoder.apply(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> unit(V value) {
        return of((buf, v) -> {
        }, buf -> value);
    }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                  Function<T1, C> factory) {
        return of((buf, v) -> c1.encode(buf, g1.apply(v)), buf -> factory.apply(c1.decode(buf)));
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                      StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                      Function2<T1, T2, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            return factory.apply(a, b);
        });
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                          StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                          StreamCodec<? super B, T3> c3, Function<C, T3> g3,
                                                          Function3<T1, T2, T3, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            T3 c = c3.decode(buf);
            return factory.apply(a, b, c);
        });
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                              StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                              StreamCodec<? super B, T3> c3, Function<C, T3> g3,
                                                              StreamCodec<? super B, T4> c4, Function<C, T4> g4,
                                                              Function4<T1, T2, T3, T4, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            T3 c = c3.decode(buf);
            T4 d = c4.decode(buf);
            return factory.apply(a, b, c, d);
        });
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                                  StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                                  StreamCodec<? super B, T3> c3, Function<C, T3> g3,
                                                                  StreamCodec<? super B, T4> c4, Function<C, T4> g4,
                                                                  StreamCodec<? super B, T5> c5, Function<C, T5> g5,
                                                                  Function5<T1, T2, T3, T4, T5, C> factory) {
        return of((buf, v) -> {
            c1.encode(buf, g1.apply(v));
            c2.encode(buf, g2.apply(v));
            c3.encode(buf, g3.apply(v));
            c4.encode(buf, g4.apply(v));
            c5.encode(buf, g5.apply(v));
        }, buf -> {
            T1 a = c1.decode(buf);
            T2 b = c2.decode(buf);
            T3 c = c3.decode(buf);
            T4 d = c4.decode(buf);
            T5 e = c5.decode(buf);
            return factory.apply(a, b, c, d, e);
        });
    }

    @FunctionalInterface
    interface Encoder<B, V> {
        void encode(B buf, V value);
    }

    @FunctionalInterface
    interface Function2<A, B, R> {
        R apply(A a, B b);
    }

    @FunctionalInterface
    interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    interface Function4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    @FunctionalInterface
    interface Function5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }
}
