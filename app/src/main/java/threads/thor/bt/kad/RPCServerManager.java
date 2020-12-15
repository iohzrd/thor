package threads.thor.bt.kad;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import threads.thor.bt.kad.utils.AddressUtils;
import threads.thor.bt.kad.utils.ThreadLocalUtils;
import threads.thor.bt.service.NetworkUtil;

public class RPCServerManager {

    private final DHT dht;
    private final AtomicReference<CompletableFuture<RPCServer>> activeServerFuture = new AtomicReference<>(null);
    private final List<Consumer<RPCServer>> onServerRegistration = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<InetAddress, RPCServer> interfacesInUse = new ConcurrentHashMap<>();
    private final SpamThrottle outgoingThrottle = new SpamThrottle();
    private final ConcurrentMap<InetAddress, Boolean> couldUseCacheMap = new ConcurrentHashMap<>();
    private final InetAddress localAddress = NetworkUtil.getInetAddressFromNetworkInterfaces();
    private boolean destroyed;
    private volatile List<InetAddress> validBindAddresses = Collections.emptyList();
    private volatile RPCServer[] activeServers = new RPCServer[0];

    RPCServerManager(DHT dht) {
        this.dht = dht;
        updateBindAddrs();
    }

    void refresh(long now, int port) {
        if (destroyed)
            return;

        startNewServers(port);

        List<RPCServer> reachableServers = new ArrayList<>(interfacesInUse.values().size());
        for (RPCServer srv : interfacesInUse.values()) {
            srv.checkReachability(now);
            if (srv.isReachable())
                reachableServers.add(srv);
        }

        if (reachableServers.size() > 0) {
            CompletableFuture<RPCServer> cf = activeServerFuture.getAndSet(null);
            if (cf != null) {
                cf.complete(reachableServers.get(ThreadLocalRandom.current().nextInt(reachableServers.size())));
            }
        }

        activeServers = reachableServers.toArray(new RPCServer[reachableServers.size()]);
    }

    private void updateBindAddrs() {
        try {
            Class<? extends InetAddress> type = dht.getType().PREFERRED_ADDRESS_TYPE;

            List<InetAddress> oldBindAddresses = validBindAddresses;

            List<InetAddress> newBindAddrs = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(iface -> iface.getInterfaceAddresses().stream())
                    .map(InterfaceAddress::getAddress)
                    .filter(type::isInstance)
                    .distinct()
                    .collect(Collectors.toCollection(ArrayList::new));

            newBindAddrs.add(AddressUtils.getAnyLocalAddress(type));

            newBindAddrs.removeIf(normalizedAddressPredicate().negate());

            if (!oldBindAddresses.equals(newBindAddrs)) {
                DHT.logInfo("updating set of valid bind addresses\n old: " + oldBindAddresses + "\n new: " + newBindAddrs);
            }

            validBindAddresses = newBindAddrs;

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void doBindChecks() {
        updateBindAddrs();
        Collection<InetAddress> valid = validBindAddresses;
        getAllServers().forEach(srv -> {
            InetAddress addr = srv.getBindAddress();
            if (!valid.contains(addr)) {
                DHT.logInfo("bind address no longer valid, removing from active set: " + addr);
                srv.stop();
            }
        });

    }

    public Predicate<InetAddress> filterBindAddress() {
        return address -> {
            Boolean couldUse = couldUseCacheMap.get(address);
            if (couldUse != null) {
                return couldUse;
            }

            boolean bothAnyLocal = address.isAnyLocalAddress() && localAddress.isAnyLocalAddress();
            couldUse = bothAnyLocal || localAddress.equals(address);

            couldUseCacheMap.put(address, couldUse);
            return couldUse;
        };
    }


    private Predicate<InetAddress> normalizedAddressPredicate() {
        Predicate<InetAddress> pred = filterBindAddress();
        return (addr) -> {
            if (pred.test(AddressUtils.getAnyLocalAddress(addr.getClass()))) {
                return true;
            }
            return pred.test(addr);
        };
    }

    private void startNewServers(int port) {

        Class<? extends InetAddress> addressType = dht.getType().PREFERRED_ADDRESS_TYPE;

        Predicate<InetAddress> addressFilter = normalizedAddressPredicate();


        // single home
        RPCServer current = interfacesInUse.values().stream().findAny().orElse(null);
        InetAddress defaultBind = Optional.ofNullable(AddressUtils.getDefaultRoute(addressType)).filter(addressFilter).orElse(null);

        // check if we have bound to an anylocaladdress because we didn't know any better and consensus converged on a local address
        // that's mostly going to happen on v6 if we can't find a default route for v6
        // no need to recheck address filter since it allowed any local address bind in the first place
        if (current != null && current.getBindAddress().isAnyLocalAddress() && current.getConsensusExternalAddress() != null && AddressUtils.isValidBindAddress(current.getConsensusExternalAddress().getAddress())) {
            InetAddress rebindAddress = current.getConsensusExternalAddress().getAddress();
            DHT.logInfo("rebinding any local to" + rebindAddress);
            current.stop();
            newServer(rebindAddress, port);
            return;
        }

        // default bind changed and server is not reachable anymore. this may happen when an interface is nominally still available but not routable anymore. e.g. ipv6 temporary addresses
        if (current != null && defaultBind != null && !current.getBindAddress().equals(defaultBind) && !current.isReachable() && current.age().getSeconds() > TimeUnit.MINUTES.toSeconds(2)) {
            DHT.logInfo("stopping currently unreachable " + current.getBindAddress() + "to bind to new default route" + defaultBind);
            current.stop();
            newServer(defaultBind, port);
            return;
        }

        // single homed & already have a server -> no need for another one
        if (current != null)
            return;

        // this is our default strategy.
        if (defaultBind != null) {
            DHT.logInfo("selecting default route bind" + defaultBind);
            newServer(defaultBind, port);
            return;
        }

        // last resort for v6, try a random global unicast address, otherwise anylocal
        if (addressType.isAssignableFrom(Inet6Address.class)) {
            InetAddress addr = AddressUtils.getAvailableGloballyRoutableAddrs(addressType)
                    .stream()
                    .filter(addressFilter)
                    .findAny()
                    .orElse(Optional.of(AddressUtils.getAnyLocalAddress(addressType))
                            .filter(addressFilter)
                            .orElse(null));
            if (addr != null) {
                newServer(addr, port);
                DHT.logInfo("Last resort address selection" + addr);
            }
            return;
        }

        // last resort v4: try any-local address first. If the address filter forbids that we try any of the interface addresses including non-global ones
        Stream.concat(Stream.of(AddressUtils.getAnyLocalAddress(addressType)), AddressUtils.nonlocalAddresses()
                .filter(dht.getType()::canUseAddress))
                .filter(addressFilter)
                .findFirst()
                .ifPresent(addr -> {
                    DHT.logInfo("last resort address selection " + addr);
                    newServer(addr, port);
                });
    }

    private void newServer(InetAddress addr, int port) {
        RPCServer srv = new RPCServer(this, dht, addr, port);
        if (interfacesInUse.putIfAbsent(addr, srv) == null) {
            srv.setOutgoingThrottle(outgoingThrottle);
            onServerRegistration.forEach(c -> c.accept(srv));
            // doing the socket setup takes time, do it in the background
            dht.getScheduler().execute(srv::start);
        } else {
            srv.stop();
        }
    }

    void notifyOnServerAdded(Consumer<RPCServer> toNotify) {
        onServerRegistration.add(toNotify);
    }

    void serverRemoved(RPCServer srv) {
        interfacesInUse.remove(srv.getBindAddress(), srv);
        dht.getTaskManager().removeServer(srv);
    }

    public void destroy() {
        destroyed = true;
        new ArrayList<>(interfacesInUse.values()).parallelStream().forEach(RPCServer::stop);

        CompletableFuture<RPCServer> cf = activeServerFuture.getAndSet(null);
        if (cf != null) {
            cf.completeExceptionally(new DHTException("could not obtain active server, DHT was shut down"));
        }

    }

    public int getServerCount() {
        return interfacesInUse.size();
    }

    public int getActiveServerCount() {
        return activeServers.length;
    }

    public SpamThrottle getOutgoingRequestThrottle() {
        return outgoingThrottle;
    }

    /**
     * @param fallback tries to return an inactive server if no active one can be found
     * @return a random active server, or <code>null</code> if none can be found
     */
    public RPCServer getRandomActiveServer(boolean fallback) {
        RPCServer[] srvs = activeServers;
        if (srvs.length == 0)
            return fallback ? getRandomServer() : null;
        return srvs[ThreadLocalUtils.getThreadLocalRandom().nextInt(srvs.length)];
    }

    public CompletableFuture<RPCServer> awaitActiveServer() {
        return activeServerFuture.updateAndGet(existing -> {
            if (existing != null)
                return existing;
            return new CompletableFuture<>();
        });
    }

    /**
     * may return null
     */
    public RPCServer getRandomServer() {
        List<RPCServer> servers = getAllServers();
        if (servers.isEmpty())
            return null;
        return servers.get(ThreadLocalUtils.getThreadLocalRandom().nextInt(servers.size()));
    }

    public List<RPCServer> getAllServers() {
        return new ArrayList<>(interfacesInUse.values());
    }

}
