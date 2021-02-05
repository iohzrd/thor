package threads.thor.magnet.service;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.Optional;


public class LifecycleBinding {

    private final String description;
    private final Runnable r;
    private final boolean async;

    private LifecycleBinding(@NonNull String description,
                             @NonNull Runnable r, boolean async) {
        this.description = description;
        this.r = r;
        this.async = async;
    }

    public static Builder bind(@NonNull Runnable r) {
        return new Builder(r);
    }

    public Optional<String> getDescription() {
        return Optional.of(description);
    }


    public Runnable getRunnable() {
        return r;
    }


    public boolean isAsync() {
        return async;
    }


    public static class Builder {

        private final Runnable r;
        private String description = "Unknown runnable";
        private boolean async;

        private Builder(@NonNull Runnable r) {
            this.r = Objects.requireNonNull(r);
        }


        public Builder description(@NonNull String description) {
            this.description = Objects.requireNonNull(description);
            return this;
        }

        public Builder async() {
            this.async = true;
            return this;
        }


        @NonNull
        public LifecycleBinding build() {
            return new LifecycleBinding(description, r, async);
        }
    }
}
