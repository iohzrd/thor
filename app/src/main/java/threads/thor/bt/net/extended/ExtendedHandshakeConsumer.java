/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

package threads.thor.bt.net.extended;

import java.util.ArrayList;
import java.util.List;

import threads.thor.bt.IConsumers;
import threads.thor.bt.bencoding.model.BEInteger;
import threads.thor.bt.net.IPeerConnectionPool;
import threads.thor.bt.net.InetPeer;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.extended.ExtendedHandshake;
import threads.thor.bt.torrent.messaging.MessageConsumer;
import threads.thor.bt.torrent.messaging.MessageContext;

public class ExtendedHandshakeConsumer implements IConsumers {

    private final IPeerConnectionPool connectionPool;

    public ExtendedHandshakeConsumer(IPeerConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof ExtendedHandshake) {
            consume((ExtendedHandshake) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<ExtendedHandshake>() {
            @Override
            public Class<ExtendedHandshake> getConsumedType() {
                return ExtendedHandshake.class;
            }

            @Override
            public void consume(ExtendedHandshake message, MessageContext context) {
                doConsume(message, context);
            }
        });
        return list;
    }

    private void consume(ExtendedHandshake message, MessageContext messageContext) {
        BEInteger peerListeningPort = message.getPort();
        if (peerListeningPort != null) {
            InetPeer peer = (InetPeer) messageContext.getConnectionKey().getPeer();
            int listeningPort = peerListeningPort.getValue().intValue();
            peer.setPort(listeningPort);

            connectionPool.checkDuplicateConnections(messageContext.getConnectionKey().getTorrentId(), peer);
        }
    }
}
