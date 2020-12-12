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

/**
 * Handles new peer connections.
 *
 * @since 1.0
 */
public interface ConnectionHandler {

    /**
     * Determines whether the connection can be established or should be immediately dropped.
     * Implementations are free (and often expected) to receive and send messages
     * via the provided connection.
     *
     * @param connection Connection with remote peer
     * @return true if it is safe to proceed with establishing the connection,
     * false if this connection should (is recommended to) be dropped,
     * @since 1.0
     */
    boolean handleConnection(PeerConnection connection);
}
