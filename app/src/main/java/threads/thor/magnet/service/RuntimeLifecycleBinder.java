package threads.thor.magnet.service;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class RuntimeLifecycleBinder {

    private final Map<LifecycleEvent, List<LifecycleBinding>> bindings;

    public RuntimeLifecycleBinder() {
        bindings = new HashMap<>();
        for (LifecycleEvent event : LifecycleEvent.values()) {
            bindings.put(event, new ArrayList<>());
        }
    }

    public void onStartup(@NonNull String description, @NonNull Runnable r) {
        Objects.requireNonNull(bindings.get(LifecycleEvent.STARTUP)).
                add(LifecycleBinding.bind(r).description(description).build());
    }


    public void onStartup(@NonNull LifecycleBinding binding) {
        Objects.requireNonNull(bindings.get(LifecycleEvent.STARTUP)).add(binding);
    }


    public void onShutdown(@NonNull String description, @NonNull Runnable r) {
        Objects.requireNonNull(bindings.get(LifecycleEvent.SHUTDOWN)).add(
                LifecycleBinding.bind(r).description(description).async().build());
    }


    public void addBinding(@NonNull LifecycleEvent event, @NonNull LifecycleBinding binding) {
        Objects.requireNonNull(bindings.get(event)).add(binding);
    }


    public void visitBindings(@NonNull LifecycleEvent event,
                              @NonNull Consumer<LifecycleBinding> consumer) {
        Objects.requireNonNull(bindings.get(event)).forEach(consumer);
    }


    /**
     * Lifecycle events
     *
     * @since 1.0
     */
    public enum LifecycleEvent {

        /**
         * Runtime startup
         *
         * @since 1.0
         */
        STARTUP,

        /**
         * Runtime shutdown
         *
         * @since 1.0
         */
        SHUTDOWN
    }
}
