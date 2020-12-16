package threads.thor.bt.net.portmapping.impl;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.util.Set;

import threads.thor.bt.net.portmapping.PortMapper;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.service.LifecycleBinding;
import threads.thor.bt.service.RuntimeLifecycleBinder;

import static threads.thor.bt.net.portmapping.PortMapProtocol.TCP;

public class PortMappingInitializer {

    public static void portMappingInitializer(@NonNull Set<PortMapper> portMappers,
                                              @NonNull RuntimeLifecycleBinder lifecycleBinder,
                                              @NonNull Config config) {

        final int acceptorPort = config.getAcceptorPort();
        final InetAddress acceptorAddress = config.getAcceptorAddress();

        lifecycleBinder.onStartup(LifecycleBinding.bind(() ->
                portMappers.forEach(m -> mapPort(acceptorPort, acceptorAddress, m)))
                .build());
    }

    private static void mapPort(int acceptorPort, InetAddress acceptorAddress, PortMapper m) {
        m.mapPort(acceptorPort, acceptorAddress.toString(), TCP, "bt acceptor");
    }
}
