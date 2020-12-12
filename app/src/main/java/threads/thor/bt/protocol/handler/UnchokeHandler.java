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

import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.DecodingContext;
import threads.thor.bt.protocol.EncodingContext;
import threads.thor.bt.protocol.Unchoke;

import static threads.thor.bt.protocol.Protocols.verifyPayloadHasLength;

public final class UnchokeHandler extends UniqueMessageHandler<Unchoke> {

    public UnchokeHandler() {
        super(Unchoke.class);
    }

    @Override
    public int doDecode(DecodingContext context, ByteBufferView buffer) {
        verifyPayloadHasLength(Unchoke.class, 0, buffer.remaining());
        context.setMessage(Unchoke.instance());
        return 0;
    }

    @Override
    public boolean doEncode(EncodingContext context, Unchoke message, ByteBuffer buffer) {
        return true;
    }
}
