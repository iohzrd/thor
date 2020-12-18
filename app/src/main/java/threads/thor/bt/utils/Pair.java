package threads.thor.bt.utils;

import java.util.function.Function;

public class Pair<A, B> {

    public final A a;
    public final B b;

    private Pair(A a, B b) {
        this.a = a;
        this.b = b;
    }

    public static <C, D> Function<D, Pair<C, D>> of(C a) {
        return b -> new Pair<>(a, b);
    }


}
