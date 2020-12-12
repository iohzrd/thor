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

package threads.thor.bt.peerexchange;

import androidx.annotation.NonNull;

import threads.thor.bt.net.Peer;

class PeerEvent implements Comparable<PeerEvent> {

    private final Type type;
    private final Peer peer;
    private final long instant;

    private PeerEvent(Type type, Peer peer) {

        this.type = type;
        this.peer = peer;

        instant = System.currentTimeMillis();
    }

    static PeerEvent added(Peer peer) {
        return new PeerEvent(Type.ADDED, peer);
    }

    static PeerEvent dropped(Peer peer) {
        return new PeerEvent(Type.DROPPED, peer);
    }

    Type getType() {
        return type;
    }

    Peer getPeer() {
        return peer;
    }

    long getInstant() {
        return instant;
    }

    @Override
    public int compareTo(PeerEvent o) {

        if (instant == o.getInstant()) {
            return 0;
        } else if (instant - o.getInstant() >= 0) {
            return 1;
        } else {
            return -1;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "PeerEvent {type=" + type + ", peer=" + peer + ", instant=" + instant + '}';
    }

    enum Type {ADDED, DROPPED}
}
