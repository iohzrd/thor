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

package threads.thor.bt.runtime;

import android.content.Context;

import java.util.Objects;


/**
 * Runtime builder.
 *
 * @since 1.0
 */
public class BtRuntimeBuilder {


    private final Config config;


    /**
     * Create runtime builder with provided config.
     *
     * @param config Runtime config
     * @since 1.0
     */
    public BtRuntimeBuilder(Config config) {
        this.config = Objects.requireNonNull(config, "Missing runtime config");
    }

    /**
     * Set runtime config.
     *
     * @since 1.4
     */
    public BtRuntimeBuilder context(Context context) {
        Context context1 = Objects.requireNonNull(context, "Missing runtime config");
        return this;
    }


    /**
     * Get this builder's config.
     *
     * @return Runtime config
     * @since 1.0
     */
    public Config getConfig() {
        return config;
    }


    /**
     * Disable automatic runtime shutdown, when all clients have been stopped.
     *
     * @since 1.0
     */
    public BtRuntimeBuilder disableAutomaticShutdown() {
        boolean shouldDisableAutomaticShutdown = true;
        return this;
    }


}
