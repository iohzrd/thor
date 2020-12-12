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

package threads.thor.bt.peerexchange;

import java.time.Duration;

public class PeerExchangeConfig {

    private Duration minMessageInterval;
    private Duration maxMessageInterval;
    private int minEventsPerMessage;
    private int maxEventsPerMessage;

    public PeerExchangeConfig() {
        this.minMessageInterval = Duration.ofMinutes(1);
        this.maxMessageInterval = Duration.ofMinutes(5);
        this.minEventsPerMessage = 10;
        this.maxEventsPerMessage = 50;
    }

    /**
     * @since 1.0
     */
    public Duration getMinMessageInterval() {
        return minMessageInterval;
    }

    /**
     * @param minMessageInterval Minimal interval between sending peer exchange messages to a peer
     * @since 1.0
     */
    public void setMinMessageInterval(Duration minMessageInterval) {
        this.minMessageInterval = minMessageInterval;
    }

    /**
     * @since 1.0
     */
    public int getMinEventsPerMessage() {
        return minEventsPerMessage;
    }

    /**
     * @param minEventsPerMessage Minimal amount of events in a peer exchange message
     * @since 1.0
     */
    public void setMinEventsPerMessage(int minEventsPerMessage) {
        this.minEventsPerMessage = minEventsPerMessage;
    }

    /**
     * @since 1.0
     */
    public int getMaxEventsPerMessage() {
        return maxEventsPerMessage;
    }

    /**
     * @param maxEventsPerMessage Maximal amount of events in a peer exchange message
     * @since 1.0
     */
    public void setMaxEventsPerMessage(int maxEventsPerMessage) {
        this.maxEventsPerMessage = maxEventsPerMessage;
    }

    /**
     * @since 1.9
     */
    public Duration getMaxMessageInterval() {
        return maxMessageInterval;
    }

    /**
     * @param maxMessageInterval Maximal interval between sending peer exchange messages to a peer
     * @since 1.9
     */
    public void setMaxMessageInterval(Duration maxMessageInterval) {
        this.maxMessageInterval = maxMessageInterval;
    }
}
