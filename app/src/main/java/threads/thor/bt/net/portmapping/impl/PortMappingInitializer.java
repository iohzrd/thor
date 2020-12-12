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

package threads.thor.bt.net.portmapping.impl;

import java.net.InetAddress;
import java.util.Set;

import threads.thor.bt.net.portmapping.PortMapper;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.service.LifecycleBinding;
import threads.thor.bt.service.RuntimeLifecycleBinder;

import static threads.thor.bt.net.portmapping.PortMapProtocol.TCP;

/**
 * Initializes port mappings on application startup.
 */

public class PortMappingInitializer {

    public PortMappingInitializer(Set<PortMapper> portMappers, RuntimeLifecycleBinder lifecycleBinder, Config config) {

        final int acceptorPort = config.getAcceptorPort();
        final InetAddress acceptorAddress = config.getAcceptorAddress();

        lifecycleBinder.onStartup(LifecycleBinding.bind(() ->
                portMappers.forEach(m -> mapPort(acceptorPort, acceptorAddress, m)))
                .build());
    }

    private void mapPort(int acceptorPort, InetAddress acceptorAddress, PortMapper m) {
        m.mapPort(acceptorPort, acceptorAddress.toString(), TCP, "bt acceptor");
    }
}
