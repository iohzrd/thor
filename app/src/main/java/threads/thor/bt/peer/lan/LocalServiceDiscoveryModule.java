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

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import threads.thor.bt.module.PeerConnectionSelector;
import threads.thor.bt.net.PeerConnectionAcceptor;
import threads.thor.bt.net.SharedSelector;
import threads.thor.bt.net.SocketChannelConnectionAcceptor;
import threads.thor.bt.service.RuntimeLifecycleBinder;

/**
 * @since 1.6
 */
public class LocalServiceDiscoveryModule {


    public static Cookie provideLocalServiceDiscoveryCookie() {
        return Cookie.newCookie();
    }

    public static LocalServiceDiscoveryInfo provideLocalServiceDiscoveryInfo(
            Set<PeerConnectionAcceptor> connectionAcceptors,
            LocalServiceDiscoveryConfig config) {

        Set<SocketChannelConnectionAcceptor> socketAcceptors = connectionAcceptors.stream()
                .filter(a -> a instanceof SocketChannelConnectionAcceptor)
                .map(a -> (SocketChannelConnectionAcceptor) a)
                .collect(Collectors.toSet());

        return new LocalServiceDiscoveryInfo(socketAcceptors, config.getLocalServiceDiscoveryAnnounceGroups());
    }


    public static Collection<AnnounceGroupChannel> provideGroupChannels(
            LocalServiceDiscoveryInfo info,
            @PeerConnectionSelector SharedSelector selector,
            RuntimeLifecycleBinder lifecycleBinder) {

        Collection<AnnounceGroupChannel> groupChannels = info.getCompatibleGroups().stream()
                .map(g -> new AnnounceGroupChannel(g, selector, info.getNetworkInterfaces()))
                .collect(Collectors.toList());

        lifecycleBinder.onShutdown(() -> groupChannels.forEach(AnnounceGroupChannel::closeQuietly));

        return groupChannels;
    }
}
