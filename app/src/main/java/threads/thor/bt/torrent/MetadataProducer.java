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

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import threads.thor.bt.Config;
import threads.thor.bt.IConsumers;
import threads.thor.bt.IProduces;
import threads.thor.bt.magnet.UtMetadata;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.net.Peer;
import threads.thor.bt.protocol.Message;

public class MetadataProducer implements IProduces, IConsumers {

    private final Torrent torrentSupplier;
    private final ConcurrentMap<Peer, Queue<Message>> outboundMessages;
    private final int metadataExchangeBlockSize;
    // initialized on the first metadata request if the threads.torrent is present
    private volatile ExchangedMetadata metadata;

    public MetadataProducer(@Nullable Torrent torrentSupplier,
                            Config config) {
        this.torrentSupplier = torrentSupplier;
        this.outboundMessages = new ConcurrentHashMap<>();
        this.metadataExchangeBlockSize = config.getMetadataExchangeBlockSize();
    }

    @Override
    public void doConsume(Message message, MessageContext messageContext) {
        if (message instanceof UtMetadata) {
            consume((UtMetadata) message, messageContext);
        }
    }

    @Override
    public List<MessageConsumer<? extends Message>> getConsumers() {
        List<MessageConsumer<? extends Message>> list = new ArrayList<>();
        list.add(new MessageConsumer<UtMetadata>() {
            @Override
            public Class<UtMetadata> getConsumedType() {
                return UtMetadata.class;
            }

            @Override
            public void consume(UtMetadata message, MessageContext context) {
                doConsume(message, context);
            }
        });
        return list;
    }

    private void consume(UtMetadata message, MessageContext context) {
        Peer peer = context.getPeer();
        // being lenient herer and not checking if the peer advertised ut_metadata support
        if (message.getType() == UtMetadata.Type.REQUEST) {// TODO: spam protection
            processMetadataRequest(peer, message.getPieceIndex());
        }// ignore
    }

    private void processMetadataRequest(Peer peer, int pieceIndex) {
        Message response;

        Torrent torrent = torrentSupplier;
        if (torrent == null || torrent.isPrivate()) {
            // reject all requests if:
            // - we don't have the torrent yet
            // - torrent is private
            response = UtMetadata.reject(pieceIndex);
        } else {
            if (metadata == null) {
                metadata = new ExchangedMetadata(torrent.getSource().getExchangedMetadata(), metadataExchangeBlockSize);
            }

            response = UtMetadata.data(pieceIndex, metadata.length(), metadata.getBlock(pieceIndex));
        }

        getOrCreateOutboundMessages(peer).add(response);
    }

    private Queue<Message> getOrCreateOutboundMessages(Peer peer) {
        Queue<Message> queue = outboundMessages.get(peer);
        if (queue == null) {
            queue = new LinkedBlockingQueue<>();
            Queue<Message> existing = outboundMessages.putIfAbsent(peer, queue);
            if (existing != null) {
                queue = existing;
            }
        }
        return queue;
    }


    public void produce(Consumer<Message> messageConsumer, MessageContext context) {
        Peer peer = context.getPeer();

        Queue<Message> queue = outboundMessages.get(peer);
        if (queue != null && queue.size() > 0) {
            messageConsumer.accept(queue.poll());
        }
    }
}
