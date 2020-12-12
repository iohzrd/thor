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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.PeerId;
import threads.thor.bt.service.RuntimeLifecycleBinder;
import threads.thor.bt.tracker.Tracker;
import threads.thor.bt.tracker.TrackerRequestBuilder;
import threads.thor.bt.tracker.TrackerResponse;
import threads.thor.bt.tracker.udp.AnnounceRequest.EventType;

/**
 * Simple implementation of a UDP tracker client
 *
 * @since 1.0
 */
class UdpTracker implements Tracker {


    private final PeerId peerId;
    private final int listeningPort;
    private final int numberOfPeersToRequestFromTracker;
    private final URL trackerUrl;
    private final UdpMessageWorker worker;


    UdpTracker(@NonNull PeerId peerId,
               RuntimeLifecycleBinder lifecycleBinder,
               InetAddress localAddress,
               int listeningPort,
               int numberOfPeersToRequestFromTracker,
               String trackerUrl) {
        this.peerId = peerId;
        this.listeningPort = listeningPort;
        this.numberOfPeersToRequestFromTracker = numberOfPeersToRequestFromTracker;
        // TODO: one UDP socket for all outgoing tracker connections
        this.trackerUrl = toUrl(trackerUrl);
        this.worker = new UdpMessageWorker(new InetSocketAddress(localAddress, 0), getSocketAddress(this.trackerUrl), lifecycleBinder);
    }

    private URL toUrl(String s) {
        if (!s.startsWith("udp://")) {
            throw new IllegalArgumentException("Unexpected URL format: " + s);
        }
        // workaround for java.net.MalformedURLException (unsupported protocol: udp)
        s = s.replace("udp://", "http://");

        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + s);
        }
    }

    private SocketAddress getSocketAddress(URL url) {
        String host = Objects.requireNonNull(url.getHost(), "Host name is required");
        int port = getPort(url);
        return new InetSocketAddress(host, port);
    }

    private int getPort(URL url) {
        int port = url.getPort();
        if (port < 0) {
            port = url.getDefaultPort();
            if (port < 0) {
                throw new IllegalArgumentException("Can't determine port from URL: " + url.toExternalForm());
            }
        }
        return port;
    }

    @Override
    public TrackerRequestBuilder request(TorrentId torrentId) {
        return new TrackerRequestBuilder(torrentId) {
            @Override
            public TrackerResponse start() {
                return announceEvent(EventType.START);
            }

            @Override
            public TrackerResponse stop() {
                return announceEvent(EventType.STOP);
            }

            @Override
            public TrackerResponse complete() {
                return announceEvent(EventType.COMPLETE);
            }

            @Override
            public TrackerResponse query() {
                return announceEvent(EventType.QUERY);
            }

            private TrackerResponse announceEvent(EventType eventType) {
                AnnounceRequest request = new AnnounceRequest();
                request.setTorrentId(getTorrentId());
                request.setPeerId(peerId);
                request.setEventType(eventType);
                request.setListeningPort((short) listeningPort);

                request.setDownloaded(getDownloaded());
                request.setUploaded(getUploaded());
                request.setLeft(getLeft());
                request.setNumwant(numberOfPeersToRequestFromTracker);

                getRequestString(trackerUrl).ifPresent(request::setRequestString);

                try {
                    return worker.sendMessage(request, AnnounceResponseHandler.handler());
                } catch (Exception e) {
                    return TrackerResponse.exceptional(e);
                }
            }
        };
    }

    private Optional<String> getRequestString(URL url) {
        String result = url.getPath();
        if (url.getQuery() != null) {
            result += "?" + url.getQuery();
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    @NonNull
    @Override
    public String toString() {
        return "UdpTracker{" +
                "trackerUrl=" + trackerUrl +
                '}';
    }
}
