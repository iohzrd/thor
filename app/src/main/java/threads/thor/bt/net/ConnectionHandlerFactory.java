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

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.protocol.HandshakeFactory;
import threads.thor.bt.torrent.TorrentRegistry;

/**
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class ConnectionHandlerFactory implements IConnectionHandlerFactory {

    private final HandshakeFactory handshakeFactory;
    private final ConnectionHandler incomingHandler;
    private final Duration peerHandshakeTimeout;

    private final Collection<HandshakeHandler> handshakeHandlers;

    private final Map<TorrentId, ConnectionHandler> outgoingHandlers;

    public ConnectionHandlerFactory(HandshakeFactory handshakeFactory,
                                    TorrentRegistry torrentRegistry,
                                    Collection<HandshakeHandler> handshakeHandlers,
                                    Duration peerHandshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.incomingHandler = new IncomingHandshakeHandler(handshakeFactory, torrentRegistry,
                handshakeHandlers, peerHandshakeTimeout);

        this.outgoingHandlers = new ConcurrentHashMap<>();
        this.handshakeHandlers = handshakeHandlers;

        this.peerHandshakeTimeout = peerHandshakeTimeout;
    }

    @Override
    public ConnectionHandler getIncomingHandler() {
        return incomingHandler;
    }

    @Override
    public ConnectionHandler getOutgoingHandler(TorrentId torrentId) {
        Objects.requireNonNull(torrentId, "Missing threads.torrent ID");
        ConnectionHandler outgoing = outgoingHandlers.get(torrentId);
        if (outgoing == null) {
            outgoing = new OutgoingHandshakeHandler(handshakeFactory, torrentId,
                    handshakeHandlers, peerHandshakeTimeout.toMillis());
            ConnectionHandler existing = outgoingHandlers.putIfAbsent(torrentId, outgoing);
            if (existing != null) {
                outgoing = existing;
            }
        }
        return outgoing;
    }
}
