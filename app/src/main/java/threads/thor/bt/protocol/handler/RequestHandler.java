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

package threads.thor.bt.protocol.handler;

import java.nio.ByteBuffer;
import java.util.Objects;

import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.InvalidMessageException;
import threads.thor.bt.protocol.Request;

import static threads.thor.bt.protocol.Protocols.readInt;
import static threads.thor.bt.protocol.Protocols.verifyPayloadHasLength;

public final class RequestHandler extends UniqueMessageHandler<Request> {

    public RequestHandler() {
        super(Request.class);
    }

    // request: <len=0013><id=6><index><begin><length>
    private static boolean writeRequest(int pieceIndex, int offset, int length, ByteBuffer buffer) {

        if (pieceIndex < 0 || offset < 0 || length <= 0) {
            throw new InvalidMessageException("Invalid arguments: pieceIndex (" + pieceIndex
                    + "), offset (" + offset + "), length (" + length + ")");
        }
        if (buffer.remaining() < Integer.BYTES * 3) {
            return false;
        }

        buffer.putInt(pieceIndex);
        buffer.putInt(offset);
        buffer.putInt(length);

        return true;
    }

    private static int decodeRequest(DecodingContext context, ByteBufferView buffer) {

        int consumed = 0;
        int length = Integer.BYTES * 3;

        if (buffer.remaining() >= length) {

            int pieceIndex = Objects.requireNonNull(readInt(buffer));
            int blockOffset = Objects.requireNonNull(readInt(buffer));
            int blockLength = Objects.requireNonNull(readInt(buffer));

            context.setMessage(new Request(pieceIndex, blockOffset, blockLength));
            consumed = length;
        }

        return consumed;
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(Request.class, 12, buffer.remaining());
        return decodeRequest(context, buffer);
    }

    @Override
    public boolean doEncode(EncodingContext context, Request message, ByteBuffer buffer) {
        return writeRequest(message.getPieceIndex(), message.getOffset(), message.getLength(), buffer);
    }
}
