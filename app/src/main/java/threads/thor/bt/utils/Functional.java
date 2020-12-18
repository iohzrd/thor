package threads.thor.bt.utils;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class Functional {

    public static <T> T tap(T obj, Consumer<T> c) {
        c.accept(obj);
        return obj;
    }

    public static <T, E extends Throwable> T tapThrow(T obj, ThrowingConsumer<T, E> c) throws E {
        c.accept(obj);
        return obj;
    }

    public static <T> T unchecked(ThrowingSupplier<? extends T, ?> f) {
        try {
            return f.get();
        } catch (Throwable e) {
            throwAsUnchecked(e);
            return null;
        }
    }


    public static <IN, OUT, EX extends Throwable> Function<IN, OUT> castOrThrow(Class<OUT> type, Function<IN, EX> ex) {
        return (in) -> {
            if (!type.isInstance(in))
                throwAsUnchecked(ex.apply(in));
            return type.cast(in);
        };
    }

    private static void throwAsUnchecked(Throwable t) {
        Thrower.asUnchecked(t);
    }

    @SuppressWarnings("unchecked")
    // a supertype of the expected T can be passed to allow type-inference to match erased generics
    public static <K, T> Optional<T> typedGet(Map<? super K, ?> map, K key, Class<? super T> clazz) {
        return (Optional<T>) Optional.ofNullable(map.get(key)).filter(clazz::isInstance).map(clazz::cast);
    }


    public interface ThrowingConsumer<T, E extends Throwable> {
        void accept(T arg) throws E;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T, E extends Throwable> {
        T get() throws E;
    }


    private static class Thrower {

        @SuppressWarnings("unchecked")
        static private <T extends Throwable> void asUnchecked(Throwable t) throws T {
            throw (T) t;
        }
    }


}
