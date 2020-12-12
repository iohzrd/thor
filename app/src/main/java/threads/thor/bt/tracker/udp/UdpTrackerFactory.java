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

package threads.thor.bt.tracker.udp;

import threads.thor.bt.runtime.Config;
import threads.thor.bt.service.RuntimeLifecycleBinder;
import threads.thor.bt.tracker.Tracker;
import threads.thor.bt.tracker.TrackerFactory;

/**
 * Creates UDP tracker clients.
 *
 * @since 1.0
 */
public class UdpTrackerFactory implements TrackerFactory {


    private final RuntimeLifecycleBinder lifecycleBinder;
    private final Config config;

    public UdpTrackerFactory(RuntimeLifecycleBinder lifecycleBinder, Config config) {

        this.lifecycleBinder = lifecycleBinder;
        this.config = config;
    }

    @Override
    public Tracker getTracker(String trackerUrl) {
        return new UdpTracker(config.getLocalPeerId(), lifecycleBinder, config.getAcceptorAddress(), config.getAcceptorPort(),
                config.getNumberOfPeersToRequestFromTracker(), trackerUrl);
    }
}
