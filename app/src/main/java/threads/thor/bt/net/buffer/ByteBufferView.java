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

package threads.thor.bt.net.buffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public interface ByteBufferView {

    int position();

    ByteBufferView position(int newPosition);

    int limit();

    ByteBufferView limit(int newLimit);

    int capacity();

    boolean hasRemaining();

    int remaining();

    byte get();

    short getShort();

    int getInt();

    ByteBufferView get(byte[] dst);

    void transferTo(ByteBuffer buffer);

    int transferTo(WritableByteChannel sbc) throws IOException;

    ByteBufferView duplicate();
}
