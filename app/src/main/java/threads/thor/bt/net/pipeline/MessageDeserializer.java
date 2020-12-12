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

package threads.thor.bt.net.pipeline;

import java.util.Objects;

import threads.thor.bt.BtException;
import threads.thor.bt.net.Peer;
import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.handler.MessageHandler;

/**
 * Reads and decodes peer messages from a byte buffer.
 */
class MessageDeserializer {

    private final MessageHandler<Message> protocol;
    private final Peer peer;

    public MessageDeserializer(Peer peer, MessageHandler<Message> protocol) {
        this.peer = peer;
        this.protocol = protocol;
    }

    public Message deserialize(ByteBufferView buffer) {
        int position = buffer.position();
        int limit = buffer.limit();

        Message message = null;
        DecodingContext context = new DecodingContext(peer);
        int consumed = protocol.decode(context, buffer);
        if (consumed > 0) {
            if (consumed > limit - position) {
                throw new BtException("Unexpected amount of bytes consumed: " + consumed);
            }
            buffer.position(position + consumed);
            message = Objects.requireNonNull(context.getMessage());
        } else {
            buffer.limit(limit);
            buffer.position(position);
        }
        return message;
    }
}
