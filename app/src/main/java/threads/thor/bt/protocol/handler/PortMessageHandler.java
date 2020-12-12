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

import threads.thor.bt.BtException;
import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.InvalidMessageException;
import threads.thor.bt.protocol.Port;
import threads.thor.bt.protocol.Protocols;

import static threads.thor.bt.protocol.Protocols.getShortBytes;
import static threads.thor.bt.protocol.Protocols.verifyPayloadHasLength;

/**
 * @since 1.1
 */
public final class PortMessageHandler extends UniqueMessageHandler<Port> {

    public static final int PORT_ID = 9;

    private static final int EXPECTED_PAYLOAD_LENGTH = Short.BYTES;

    public PortMessageHandler() {
        super(Port.class);
    }

    // port: <len=0003><id=9><listen-port>
    private static boolean writePort(int port, ByteBuffer buffer) {
        if (port < 0 || port > Short.MAX_VALUE * 2 + 1) {
            throw new BtException("Invalid port: " + port);
        }
        if (buffer.remaining() < Short.BYTES) {
            return false;
        }

        buffer.put(getShortBytes(port));
        return true;
    }

    private static int decodePort(DecodingContext context, ByteBufferView buffer) throws InvalidMessageException {
        int consumed = 0;
        int length = Short.BYTES;

        Short s;
        if ((s = Protocols.readShort(buffer)) != null) {
            int port = s & 0x0000FFFF;
            context.setMessage(new Port(port));
            consumed = length;
        }

        return consumed;
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(Port.class, EXPECTED_PAYLOAD_LENGTH, buffer.remaining());
        return decodePort(context, buffer);
    }

    @Override
    public boolean doEncode(EncodingContext context, Port message, ByteBuffer buffer) {
        return writePort(message.getPort(), buffer);
    }
}
