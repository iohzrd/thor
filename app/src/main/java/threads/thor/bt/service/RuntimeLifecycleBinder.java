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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
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


    public void onShutdown(@NonNull Runnable r) {
        Objects.requireNonNull(bindings.get(LifecycleEvent.SHUTDOWN)).
                add(LifecycleBinding.bind(r).async().build());
    }


    public void onShutdown(@NonNull String description, @NonNull Runnable r) {
        Objects.requireNonNull(bindings.get(LifecycleEvent.SHUTDOWN)).add(
                LifecycleBinding.bind(r).description(description).async().build());
    }


    public void onShutdown(@NonNull LifecycleBinding binding) {
        Objects.requireNonNull(bindings.get(LifecycleEvent.SHUTDOWN)).add(binding);
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
