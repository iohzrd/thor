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

package threads.thor.bt.peer.lan;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import threads.LogUtils;
import threads.thor.bt.BufferingMap;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.InetPeer;
import threads.thor.bt.net.Peer;
import threads.thor.bt.peer.PeerSource;
import threads.thor.bt.peer.PeerSourceFactory;
import threads.thor.bt.service.LifecycleBinding;
import threads.thor.bt.service.RuntimeLifecycleBinder;

import static threads.thor.bt.peer.lan.Cookie.sameValue;

/**
 * @since 1.6
 */
public class LocalServiceDiscoveryPeerSourceFactory implements PeerSourceFactory {

    private final ByteBuffer receiveBuffer;
    private final Collection<AnnounceGroupChannel> groupChannels;
    private final Cookie cookie;
    private final BufferingMap<TorrentId, Peer> collectedPeers;


    public LocalServiceDiscoveryPeerSourceFactory(Collection<AnnounceGroupChannel> groupChannels,
                                                  RuntimeLifecycleBinder lifecycleBinder,
                                                  Cookie cookie,
                                                  LocalServiceDiscoveryConfig config) {
        this.receiveBuffer = createBuffer(config);
        this.groupChannels = groupChannels;
        this.cookie = cookie;
        this.collectedPeers = new BufferingMap<>(HashSet::new);

        // do not enable LSD if there are no groups to join
        if (groupChannels.size() > 0) {
            schedulePeriodicPeerCollection(lifecycleBinder);
        }
    }

    private static ByteBuffer createBuffer(LocalServiceDiscoveryConfig config) {
        int maxMessageSize = AnnounceMessage.calculateMessageSize(config.getLocalServiceDiscoveryMaxTorrentsPerAnnounce());
        return ByteBuffer.allocateDirect(maxMessageSize * 2);
    }

    private void schedulePeriodicPeerCollection(RuntimeLifecycleBinder lifecycleBinder) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "lsd-peer-source"));
        executor.scheduleWithFixedDelay(this::collectPeers, 1, 1, TimeUnit.SECONDS);
        lifecycleBinder.onShutdown(LifecycleBinding.bind(executor::shutdownNow).description("Shutdown LSD peer collection").build());
    }

    private void collectPeers() {
        groupChannels.forEach(channel -> {
            receiveBuffer.clear();

            SocketAddress remoteAddress = null;
            try {
                remoteAddress = channel.receive(receiveBuffer);
            } catch (Exception e) {
                LogUtils.error(LogUtils.TAG, e);
            }

            if (remoteAddress == null) {
                return;
            }

            receiveBuffer.flip();

            AnnounceMessage message = null;
            try {
                message = AnnounceMessage.readFrom(receiveBuffer);
            } catch (Exception e) {
                LogUtils.error(LogUtils.TAG, e);
            }

            if (message != null) {
                // ignore our own cookie
                if (!sameValue(cookie, message.getCookie())) {

                    collectPeers(remoteAddress, message);
                }
            }
        });
    }

    private void collectPeers(SocketAddress address, AnnounceMessage message) {
        Peer peer = InetPeer.build(((InetSocketAddress) address).getAddress(), message.getPort());
        message.getTorrentIds().forEach(id -> collectedPeers.add(id, peer));
    }

    @Override
    public PeerSource getPeerSource(TorrentId torrentId) {
        return new PeerSource() {
            private boolean updated;

            @Override
            public boolean update() {
                updated = collectedPeers.containsKey(torrentId);
                return updated;
            }

            @Override
            public Collection<Peer> getPeers() {
                Collection<Peer> peers = updated ? collectedPeers.removeCopy(torrentId) : Collections.emptyList();
                updated = false;
                return peers;
            }
        };
    }
}
