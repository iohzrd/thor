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

package threads.thor.bt.torrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.thor.bt.IAgent;
import threads.thor.bt.IConsumers;
import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.protocol.Bitfield;
import threads.thor.bt.protocol.Have;
import threads.thor.bt.protocol.Message;


/**
 * Accumulates received bitfields and haves without validating anything.
 * Used when exact threads.torrent parameters like number of pieces is not known yet, e.g. when fetching metadata from peers.
 *
 * @since 1.3
 */
public class BitfieldCollectingConsumer implements IAgent, IConsumers {

    private final ConcurrentMap<ConnectionKey, byte[]> bitfields;
    private final ConcurrentMap<ConnectionKey, Set<Integer>> haves;

    public BitfieldCollectingConsumer() {
        this.bitfields = new ConcurrentHashMap<>();
        this.haves = new ConcurrentHashMap<>();
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof Bitfield) {
            consume((Bitfield) message, messageContext);
        }
        if (message instanceof Have) {
            consume((Have) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<Bitfield>() {
            @Override
            public Class<Bitfield> getConsumedType() {
                return Bitfield.class;
            }

            @Override
            public void consume(Bitfield message, MessageContext context) {
                doConsume(message, context);
            }
        });
        list.add(new MessageConsumer<Have>() {
            @Override
            public Class<Have> getConsumedType() {
                return Have.class;
            }

            @Override
            public void consume(Have message, MessageContext context) {
                doConsume(message, context);
            }
        });
        return list;
    }


    private void consume(Bitfield bitfieldMessage, MessageContext context) {
        bitfields.put(context.getConnectionKey(), bitfieldMessage.getBitfield());
    }

    private void consume(Have have, MessageContext context) {
        ConnectionKey peer = context.getConnectionKey();
        Set<Integer> peerHaves = haves.computeIfAbsent(peer, k -> ConcurrentHashMap.newKeySet());
        peerHaves.add(have.getPieceIndex());
    }

    public Map<ConnectionKey, byte[]> getBitfields() {
        return bitfields;
    }

    public Map<ConnectionKey, Set<Integer>> getHaves() {
        return haves;
    }
}
