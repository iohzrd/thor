/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.service;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import threads.thor.bt.runtime.Config;

/**
 * @since 1.1
 */
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

    /**
     * @since 1.1
     */
    public static Builder bind(@NonNull Runnable r) {
        return new Builder(r);
    }

    /**
     * @since 1.1
     */
    public Optional<String> getDescription() {
        return Optional.of(description);
    }

    /**
     * @since 1.1
     */
    public Runnable getRunnable() {
        return r;
    }

    /**
     * @since 1.1
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * @since 1.1
     */
    public static class Builder {

        private final Runnable r;
        private String description = "Unknown runnable";
        private boolean async;

        private Builder(@NonNull Runnable r) {
            this.r = Objects.requireNonNull(r);
        }

        /**
         * @param description Human-readable description
         * @since 1.1
         */
        public Builder description(@NonNull String description) {
            this.description = Objects.requireNonNull(description);
            return this;
        }

        /**
         * Mark this binding for asynchronous execution
         *
         * @see Config#setShutdownHookTimeout(Duration)
         * @since 1.1
         */
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
