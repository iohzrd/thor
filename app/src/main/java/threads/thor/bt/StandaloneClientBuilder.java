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

package threads.thor.bt;

import threads.thor.bt.runtime.BtClient;
import threads.thor.bt.runtime.BtRuntime;
import threads.thor.bt.runtime.BtRuntimeBuilder;
import threads.thor.bt.runtime.Config;


/**
 * Builds a standalone client with a private runtime
 *
 * @since 1.1
 */
public class StandaloneClientBuilder extends TorrentClientBuilder<StandaloneClientBuilder> {

    private BtRuntimeBuilder runtimeBuilder;


    /**
     * Set runtime configuration.
     *
     * @since 1.1
     */
    public StandaloneClientBuilder config(Config config) {
        this.runtimeBuilder = BtRuntime.builder(config);
        return this;
    }


    @Override
    public BtClient build() {
        return super.build();
    }
}
