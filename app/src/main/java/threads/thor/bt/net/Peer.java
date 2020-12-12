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

import java.net.InetAddress;
import java.util.Optional;

import threads.thor.bt.peer.PeerOptions;

/**
 * Represents a peer, accessible on the Internet.
 *
 * @since 1.0
 */
public interface Peer {

    /**
     * @return Peer Internet address.
     * @since 1.0
     */
    InetAddress getInetAddress();

    /**
     * @return true, if the peer's listening port is not known yet
     * @see #getPort()
     * @since 1.9
     */
    boolean isPortUnknown();

    int getPort();

    /**
     * @return Optional peer ID
     * @since 1.0
     */
    Optional<PeerId> getPeerId();

    /**
     * @return Peer options and preferences
     * @since 1.2
     */
    PeerOptions getOptions();
}
