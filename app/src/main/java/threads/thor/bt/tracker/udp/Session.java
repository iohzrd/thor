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

import java.time.Duration;
import java.util.Date;

class Session {

    private static final Duration SESSION_DURATION = Duration.ofMinutes(1);
    private static final Session NO_SESSION = new Session(0x41727101980L);
    private final long id;
    private final long createdOn;

    Session(long id) {
        this.id = id;
        this.createdOn = System.currentTimeMillis();
    }

    static Session noSession() {
        return NO_SESSION;
    }

    boolean isExpired() {
        return (System.currentTimeMillis() - createdOn) >= SESSION_DURATION.toMillis();
    }

    public long getId() {
        return id;
    }

    @NonNull
    @Override
    public String toString() {
        return "Session{" +
                "id=" + id +
                ", createdOn=" + new Date(createdOn) +
                '}';
    }
}
