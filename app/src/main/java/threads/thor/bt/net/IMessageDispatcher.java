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

package threads.thor.bt.net;

import java.util.function.Consumer;
import java.util.function.Supplier;

import threads.thor.bt.protocol.Message;

/**
 * Provides access to messaging with remote peers.
 *
 * @since 1.0
 */
public interface IMessageDispatcher {

    /**
     * Add a message consumer to receive messages from a remote peer for a given threads.torrent.
     *
     * @since 1.7
     */
    void addMessageConsumer(ConnectionKey connectionKey, Consumer<Message> messageConsumer);

    /**
     * Add a message supplier to send messages to a remote peer for a given threads.torrent.
     *
     * @since 1.7
     */
    void addMessageSupplier(ConnectionKey connectionKey, Supplier<Message> messageSupplier);
}
