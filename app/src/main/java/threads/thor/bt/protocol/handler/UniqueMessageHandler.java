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

import java.util.Collection;
import java.util.Collections;

import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.bt.protocol.Message;

public abstract class UniqueMessageHandler<T extends Message> extends BaseMessageHandler<T> {

    private final Class<T> type;
    private final Collection<Class<? extends T>> supportedTypes;

    UniqueMessageHandler(Class<T> type) {
        this.type = type;
        supportedTypes = Collections.singleton(type);
    }

    @Override
    public Collection<Class<? extends T>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends T> readMessageType(ByteBufferView buffer) {
        return type;
    }
}
