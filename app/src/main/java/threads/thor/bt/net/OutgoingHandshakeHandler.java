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
import java.util.Collection;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.protocol.Handshake;
import threads.thor.bt.protocol.HandshakeFactory;
import threads.thor.bt.protocol.Message;

/**
 * Handles handshake exchange for outgoing peer connections.
 *
 * @since 1.0
 */
class OutgoingHandshakeHandler implements ConnectionHandler {


    private final HandshakeFactory handshakeFactory;
    private final TorrentId torrentId;
    private final Collection<HandshakeHandler> handshakeHandlers;
    private final long handshakeTimeout;

    OutgoingHandshakeHandler(HandshakeFactory handshakeFactory, TorrentId torrentId,
                             Collection<HandshakeHandler> handshakeHandlers, long handshakeTimeout) {
        this.handshakeFactory = handshakeFactory;
        this.torrentId = torrentId;
        this.handshakeHandlers = handshakeHandlers;
        this.handshakeTimeout = handshakeTimeout;
    }

    @Override
    public boolean handleConnection(PeerConnection connection) {
        Peer peer = connection.getRemotePeer();

        Handshake handshake = handshakeFactory.createHandshake(torrentId);
        handshakeHandlers.forEach(handler ->
                handler.processOutgoingHandshake(handshake));
        try {
            connection.postMessage(handshake);
        } catch (IOException e) {

            return false;
        }

        Message firstMessage = null;
        try {
            firstMessage = connection.readMessage(handshakeTimeout);
        } catch (IOException ignored) {
            // ignore exception
        }
        if (firstMessage != null) {
            if (Handshake.class.equals(firstMessage.getClass())) {
                Handshake peerHandshake = (Handshake) firstMessage;
                TorrentId incomingTorrentId = peerHandshake.getTorrentId();
                if (torrentId.equals(incomingTorrentId)) {
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
