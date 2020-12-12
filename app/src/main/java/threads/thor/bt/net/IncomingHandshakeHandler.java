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

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.protocol.Handshake;
import threads.thor.bt.protocol.HandshakeFactory;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.torrent.TorrentDescriptor;
import threads.thor.bt.torrent.TorrentRegistry;

/**
 * Handles handshake exchange for incoming peer connections.
 *
 * @since 1.0
 */
class IncomingHandshakeHandler implements ConnectionHandler {

    private final HandshakeFactory handshakeFactory;
    private final TorrentRegistry torrentRegistry;
    private final Collection<HandshakeHandler> handshakeHandlers;
    private final Duration handshakeTimeout;

    IncomingHandshakeHandler(HandshakeFactory handshakeFactory, TorrentRegistry torrentRegistry,
                             Collection<HandshakeHandler> handshakeHandlers, Duration handshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.torrentRegistry = torrentRegistry;
        this.handshakeHandlers = handshakeHandlers;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {
        Peer peer = connection.getRemotePeer();
        Message firstMessage = null;
        try {
            firstMessage = connection.readMessage(handshakeTimeout.toMillis());
        } catch (IOException ignored) {
            // ignore exception
        }

        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {

                Handshake peerHandshake = (Handshake) firstMessage;
                TorrentId torrentId = peerHandshake.getTorrentId();
                Optional<TorrentDescriptor> descriptorOptional = torrentRegistry.getDescriptor(torrentId);
                // it's OK if descriptor is not present -- threads.torrent might be being fetched at the time
                if (torrentRegistry.getTorrentIds().contains(torrentId)
                        && (!descriptorOptional.isPresent() || descriptorOptional.get().isActive())) {

                    Handshake handshake = handshakeFactory.createHandshake(torrentId);
                    handshakeHandlers.forEach(handler ->
                            handler.processOutgoingHandshake(handshake));

                    try {
                        connection.postMessage(handshake);
                    } catch (IOException e) {

                        return false;
                    }
                    connection.setTorrentId(torrentId);

                    handshakeHandlers.forEach(handler ->
                            handler.processIncomingHandshake(new WriteOnlyPeerConnection(connection), peerHandshake));

                    return true;
                }
            }
        }
        return false;
    }
}
