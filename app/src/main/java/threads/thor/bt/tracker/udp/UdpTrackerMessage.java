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

package threads.thor.bt.tracker.udp;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

import threads.thor.bt.protocol.Protocols;

abstract class UdpTrackerMessage {

    private final int messageType;
    private final int id;

    UdpTrackerMessage(int messageType) {
        this.messageType = messageType;
        this.id = new Random(System.currentTimeMillis()).nextInt();
    }

    public int getId() {
        return id;
    }

    int getMessageType() {
        return messageType;
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(Protocols.getIntBytes(messageType));
        out.write(Protocols.getIntBytes(id));
        writeBodyTo(out);
    }

    protected abstract void writeBodyTo(OutputStream out) throws IOException;

    @NonNull
    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "messageType=" + messageType +
                ", id=" + id +
                '}';
    }
}
