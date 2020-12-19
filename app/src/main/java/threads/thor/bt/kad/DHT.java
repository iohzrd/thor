package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import threads.LogUtils;
import threads.thor.Settings;
import threads.thor.bt.kad.GenericStorage.StorageItem;
import threads.thor.bt.kad.GenericStorage.UpdateResult;
import threads.thor.bt.kad.Node.RoutingTableEntry;
import threads.thor.bt.kad.messages.AbstractLookupRequest;
import threads.thor.bt.kad.messages.AbstractLookupResponse;
import threads.thor.bt.kad.messages.AnnounceRequest;
import threads.thor.bt.kad.messages.AnnounceResponse;
import threads.thor.bt.kad.messages.ErrorMessage;
import threads.thor.bt.kad.messages.ErrorMessage.ErrorCode;
import threads.thor.bt.kad.messages.FindNodeRequest;
import threads.thor.bt.kad.messages.FindNodeResponse;
import threads.thor.bt.kad.messages.GetPeersRequest;
import threads.thor.bt.kad.messages.GetPeersResponse;
import threads.thor.bt.kad.messages.GetRequest;
import threads.thor.bt.kad.messages.GetResponse;
import threads.thor.bt.kad.messages.MessageBase;
import threads.thor.bt.kad.messages.PingRequest;
import threads.thor.bt.kad.messages.PingResponse;
import threads.thor.bt.kad.messages.PutRequest;
import threads.thor.bt.kad.messages.PutResponse;
import threads.thor.bt.kad.messages.SampleRequest;
import threads.thor.bt.kad.messages.SampleResponse;
import threads.thor.bt.kad.messages.UnknownTypeResponse;
import threads.thor.bt.kad.tasks.AnnounceTask;
import threads.thor.bt.kad.tasks.NodeLookup;
import threads.thor.bt.kad.tasks.PeerLookupTask;
import threads.thor.bt.kad.tasks.PingRefreshTask;
import threads.thor.bt.kad.tasks.Task;
import threads.thor.bt.kad.tasks.TaskListener;
import threads.thor.bt.kad.tasks.TaskManager;
import threads.thor.bt.net.PeerId;
import threads.thor.bt.utils.NIOConnectionManager;

import static threads.thor.bt.bencode.Utils.prettyPrint;


public final class DHT {

    private static final String TAG = DHT.class.getSimpleName();
    private final ScheduledThreadPoolExecutor scheduler;
    private final ThreadGroup executorGroup;
    private final DHTtype type;
    private final RPCCallListener rpcListener;
    private final AtomicReference<BootstrapState> bootstrapping = new AtomicReference<>(BootstrapState.NONE);

    private final PopulationEstimator estimator;
    private final List<ScheduledFuture<?>> scheduledActions = new ArrayList<>();
    private final IDMismatchDetector mismatchDetector;
    private final NonReachableCache unreachableCache;
    private final NIOConnectionManager connectionManager;
    private final Node node;
    private final RPCServerManager serverManager;
    private final GenericStorage storage;
    private final Database db;
    private final TaskManager tman;
    private final AnnounceNodeCache cache;
    private long lastBootstrap;

    private Collection<InetSocketAddress> bootstrapAddresses = Collections.emptyList();

    public DHT(@NonNull DHTtype type) {
        this.type = type;

        storage = new GenericStorage();
        db = new Database();
        cache = new AnnounceNodeCache();


        node = new Node(this);
        tman = new TaskManager(this);


        //indexingListeners = new ArrayList<>();
        estimator = new PopulationEstimator();
        rpcListener = new RPCCallListener() {
            public void stateTransition(RPCCall c, RPCState previous, RPCState current) {
                if (current == RPCState.RESPONDED)
                    mismatchDetector.add(c);
                if (current == RPCState.RESPONDED || current == RPCState.TIMEOUT)
                    unreachableCache.onCallFinished(c);
                if (current == RPCState.RESPONDED || current == RPCState.TIMEOUT || current == RPCState.STALLED)
                    tman.dequeue(c.getRequest().getServer());
            }
        };

        Consumer<RPCServer> serverListener = (srv) -> {
            node.registerServer(srv);

            srv.onEnqueue((c) -> c.addListener(rpcListener));
        };
        executorGroup = new ThreadGroup("mlDHT");
        int threads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
        scheduler = new ScheduledThreadPoolExecutor(threads, r -> {
            Thread t = new Thread(executorGroup, r, "mlDHT Scheduler");

            t.setUncaughtExceptionHandler((t1, e) -> DHT.log(e));
            t.setDaemon(true);
            return t;
        });
        scheduler.setCorePoolSize(threads);
        scheduler.setKeepAliveTime(20, TimeUnit.SECONDS);
        scheduler.allowCoreThreadTimeOut(true);


        mismatchDetector = new IDMismatchDetector(this);
        unreachableCache = new NonReachableCache();


        connectionManager = new NIOConnectionManager("mlDHT " + type.shortName + " NIO Selector");
        serverManager = new RPCServerManager(this);
        serverManager.notifyOnServerAdded(serverListener);
    }


    public static void log(Throwable e) {
        LogUtils.error(TAG, e);
    }

    static void logInfo(String message) {
        LogUtils.info(TAG, message);
    }


    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }


    public void ping(PingRequest r) {

        // ignore requests we get from ourself
        if (node.isLocalId(r.getID())) {
            return;
        }

        PingResponse rsp = new PingResponse(r.getMTID());
        rsp.setDestination(r.getOrigin());
        r.getServer().sendMessage(rsp);

        node.recieved(r);
    }

    public void findNode(AbstractLookupRequest r) {

        // ignore requests we get from ourself
        if (node.isLocalId(r.getID())) {
            return;
        }

        AbstractLookupResponse response;
        if (r instanceof FindNodeRequest)
            response = new FindNodeResponse(r.getMTID());
        else
            response = new UnknownTypeResponse(r.getMTID());

        populateResponse(r.getTarget(), response, r.doesWant4() ? Settings.MAX_ENTRIES_PER_BUCKET : 0, r.doesWant6() ? Settings.MAX_ENTRIES_PER_BUCKET : 0);

        response.setDestination(r.getOrigin());
        r.getServer().sendMessage(response);

        node.recieved(r);
    }

    private void populateResponse(Key target, AbstractLookupResponse rsp, int v4, int v6) {
        if (v4 > 0) {

            KClosestNodesSearch kns = new KClosestNodesSearch(target, v4, this);
            kns.fill(DHTtype.IPV4_DHT == type);
            rsp.setNodes(kns.asNodeList());

        }

        if (v6 > 0) {

            KClosestNodesSearch kns = new KClosestNodesSearch(target, v6, this);
            kns.fill(DHTtype.IPV6_DHT == type);
            rsp.setNodes(kns.asNodeList());

        }
    }

    public void response(MessageBase r) {


        node.recieved(r);
    }

    public void get(GetRequest req) {


        GetResponse rsp = new GetResponse(req.getMTID());

        populateResponse(req.getTarget(), rsp, req.doesWant4() ?
                Settings.MAX_ENTRIES_PER_BUCKET : 0, req.doesWant6() ?
                Settings.MAX_ENTRIES_PER_BUCKET : 0);

        Key k = req.getTarget();


        Optional.ofNullable(db.genToken(req.getID(), req.getOrigin().getAddress(),
                req.getOrigin().getPort(), k)).ifPresent(token -> rsp.setToken(token.arr));

        storage.get(k).ifPresent(item -> {
            if (req.getSeq() < 0 || item.sequenceNumber < 0 || req.getSeq() < item.sequenceNumber) {
                rsp.setRawValue(ByteBuffer.wrap(item.value));
                rsp.setKey(item.pubkey);
                rsp.setSignature(item.signature);
                if (item.sequenceNumber >= 0)
                    rsp.setSequenceNumber(item.sequenceNumber);
            }
        });

        rsp.setDestination(req.getOrigin());


        req.getServer().sendMessage(rsp);

        node.recieved(req);
    }

    public void put(PutRequest req) {

        Key k = req.deriveTargetKey();

        if (!db.checkToken(new ByteWrapper(req.getToken()), req.getID(), req.getOrigin().getAddress(), req.getOrigin().getPort(), k)) {
            sendError(req, ErrorCode.ProtocolError.code, "received invalid or expired token for PUT request");
            return;
        }

        UpdateResult result = storage.putOrUpdate(k, new StorageItem(req), req.getExpectedSequenceNumber());

        switch (result) {
            case CAS_FAIL:
                sendError(req, ErrorCode.CasFail.code, "CAS failure");
                return;
            case SIG_FAIL:
                sendError(req, ErrorCode.InvalidSignature.code, "signature validation failed");
                return;
            case SEQ_FAIL:
                sendError(req, ErrorCode.CasNotMonotonic.code, "sequence number less than current");
                return;
            case IMMUTABLE_SUBSTITUTION_FAIL:
                sendError(req, ErrorCode.ProtocolError.code, "PUT request replacing mutable data with immutable is not supported");
                return;
            case SUCCESS:

                PutResponse rsp = new PutResponse(req.getMTID());
                rsp.setDestination(req.getOrigin());

                req.getServer().sendMessage(rsp);
                break;
        }


        node.recieved(req);
    }

    public void getPeers(GetPeersRequest r) {


        // ignore requests we get from ourself
        if (node.isLocalId(r.getID())) {
            return;
        }

        BloomFilterBEP33 peerFilter = r.isScrape() ? db.createScrapeFilter(r.getInfoHash(), false) : null;
        BloomFilterBEP33 seedFilter = r.isScrape() ? db.createScrapeFilter(r.getInfoHash(), true) : null;

        boolean v6 = Inet6Address.class.isAssignableFrom(type.PREFERRED_ADDRESS_TYPE);

        boolean heavyWeight = peerFilter != null;

        int valuesTargetLength = v6 ? 35 : 50;
        // scrape filter gobble up a lot of space, restrict list sizes
        if (heavyWeight)
            valuesTargetLength = v6 ? 15 : 30;

        List<DBItem> dbl = db.sample(r.getInfoHash(), valuesTargetLength, r.isNoSeeds());

        /*
        for (DHTIndexingListener listener : indexingListeners) {
            List<PeerAddressDBItem> toAdd = listener.incomingPeersRequest(r.getInfoHash(), r.getOrigin().getAddress(), r.getID());
            if (dbl == null && !toAdd.isEmpty())
                dbl = new ArrayList<>();
            if (dbl != null && !toAdd.isEmpty())
                dbl.addAll(toAdd);
        }*/

        // generate a token
        ByteWrapper token = null;
        if (db.insertForKeyAllowed(r.getInfoHash()))
            token = db.genToken(r.getID(), r.getOrigin().getAddress(), r.getOrigin().getPort(), r.getInfoHash());

        int want4 = r.doesWant4() ? Settings.MAX_ENTRIES_PER_BUCKET : 0;
        int want6 = r.doesWant6() ? Settings.MAX_ENTRIES_PER_BUCKET : 0;

        if (v6 && peerFilter != null)
            want6 = Math.min(5, want6);

        // only fulfill both wants if we have neither filters nor values to send
        if (heavyWeight || dbl != null) {
            if (v6)
                want4 = 0;
            else
                want6 = 0;
        }


        GetPeersResponse resp = new GetPeersResponse(r.getMTID());

        populateResponse(r.getTarget(), resp, want4, want6);

        resp.setToken(token != null ? token.arr : null);
        resp.setScrapePeers(peerFilter);
        resp.setScrapeSeeds(seedFilter);


        resp.setPeerItems(dbl);
        resp.setDestination(r.getOrigin());
        r.getServer().sendMessage(resp);

        node.recieved(r);
    }

    public void announce(AnnounceRequest r) {


        // ignore requests we get from ourself
        if (node.isLocalId(r.getID())) {
            return;
        }

        // first check if the token is OK
        ByteWrapper token = new ByteWrapper(r.getToken());
        if (!db.checkToken(token, r.getID(), r.getOrigin().getAddress(), r.getOrigin().getPort(), r.getInfoHash())) {
            sendError(r, ErrorCode.ProtocolError.code, "Invalid Token; tokens expire after " + Settings.TOKEN_TIMEOUT + "ms; only valid for the IP/port to which it was issued; only valid for the infohash for which it was issued");
            return;
        }

        // everything OK, so store the value
        PeerAddressDBItem item = PeerAddressDBItem.createFromAddress(r.getOrigin().getAddress(), r.getPort(), r.isSeed());
        r.getVersion().ifPresent(item::setVersion);

        db.store(r.getInfoHash(), item);

        // send a proper response to indicate everything is OK
        AnnounceResponse rsp = new AnnounceResponse(r.getMTID());
        rsp.setDestination(r.getOrigin());
        r.getServer().sendMessage(rsp);

        node.recieved(r);
    }

    public void sample(SampleRequest r) {

        SampleResponse rsp = new SampleResponse(r.getMTID());
        rsp.setSamples(db.samples());
        rsp.setDestination(r.getOrigin());
        rsp.setNum(db.getStats());
        rsp.setInterval((int) TimeUnit.MILLISECONDS.toSeconds(Settings.CHECK_FOR_EXPIRED_ENTRIES));
        populateResponse(r.getTarget(), rsp, r.doesWant4() ? Settings.MAX_ENTRIES_PER_BUCKET : 0, r.doesWant6() ? Settings.MAX_ENTRIES_PER_BUCKET : 0);

        r.getServer().sendMessage(rsp);

        node.recieved(r);
    }

    public void error(ErrorMessage r) {
        StringBuilder b = new StringBuilder();
        b.append("Error [").append(r.getCode()).append("] from: ").append(r.getOrigin());
        b.append(" Message: \"").append(r.getMessage()).append("\"");
        r.getVersion().ifPresent(v -> b.append(" version:").append(prettyPrint(v)));

        LogUtils.error(TAG, b.toString());
    }

    public void timeout(RPCCall r) {
        node.onTimeout(r);
    }

    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#addDHTNode(java.lang.String, int)
     */
    public void addDHTNode(String host, int hport) {

        InetSocketAddress addr = new InetSocketAddress(host, hport);

        if (!addr.isUnresolved()) {
            if (!type.PREFERRED_ADDRESS_TYPE.isInstance(addr.getAddress()) ||
                    node.getNumEntriesInRoutingTable() > Settings.BOOTSTRAP_IF_LESS_THAN_X_PEERS)
                return;
            RPCServer srv = serverManager.getRandomActiveServer(true);
            if (srv != null)
                srv.ping(addr);
        }

    }

    /**
     * returns a non-enqueued task for further configuration. or zero if the request cannot be serviced.
     * use the task-manager to actually start the task.
     */
    public PeerLookupTask createPeerLookup(byte[] info_hash) {

        Key id = new Key(info_hash);

        RPCServer srv = serverManager.getRandomActiveServer(false);
        if (srv == null)
            return null;

        return new PeerLookupTask(srv, node, id);
    }

    public void announce(PeerLookupTask lookup, boolean isSeed, int btPort) {


        // reuse the same server to make sure our tokens are still valid
        AnnounceTask announce = new AnnounceTask(lookup.getRPC(), node, lookup.getInfoHash(), btPort, lookup.getAnnounceCanidates());
        announce.setSeed(isSeed);

        tman.addTask(announce);

    }


    public AnnounceNodeCache getCache() {
        return cache;
    }

    public RPCServerManager getServerManager() {
        return serverManager;
    }

    NIOConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public PopulationEstimator getEstimator() {
        return estimator;
    }

    public DHTtype getType() {
        return type;
    }

    public NonReachableCache getUnreachableCache() {
        return unreachableCache;
    }


    public void start(PeerId peerId, int port) {


        LogUtils.error(TAG, "Starting DHT on port " + port);

        // we need the IPs to filter bootstrap nodes out from the routing table. but don't block startup on DNS resolution
        scheduler.execute(this::resolveBootstrapAddresses);


        node.initKey(peerId);


        // these checks are fairly expensive on large servers (network interface enumeration)
        // schedule them separately
        scheduledActions.add(scheduler.scheduleWithFixedDelay(serverManager::doBindChecks,
                10, 10, TimeUnit.SECONDS));

        // maintenance that should run all the time, before the first queries
        scheduledActions.add(scheduler.scheduleWithFixedDelay(tman::dequeue,
                5000, Settings.DHT_UPDATE_INTERVAL, TimeUnit.MILLISECONDS));

        // initialize as many RPC servers as we need
        serverManager.refresh(System.currentTimeMillis(), port);

        if (serverManager.getServerCount() == 0) {
            LogUtils.error(TAG,
                    "No network interfaces eligible for DHT sockets found during startup."
                            + "\nAddress family: " + this.getType()
                            + "\nPublic IP addresses: " + AddressUtils.getAvailableGloballyRoutableAddrs(getType().PREFERRED_ADDRESS_TYPE)
                            + "\nDefault route: " + AddressUtils.getDefaultRoute(getType().PREFERRED_ADDRESS_TYPE));
        }

        started(port);

    }

    public void started(int port) {

        for (RoutingTableEntry bucket : node.table().list()) {
            RPCServer srv = serverManager.getRandomServer();
            if (srv == null)
                break;
            Task t = new PingRefreshTask(srv, node, bucket.getBucket(), true);
            t.setInfo("Startup ping for " + bucket.prefix);
            if (t.getTodoCount() > 0)
                tman.addTask(t);
        }


        bootstrap();

		/*
		if(type == DHTtype.IPV6_DHT)
		{
			Task t = new KeyspaceCrawler(srv, node);
			tman.addTask(t);
		}*/

        scheduledActions.add(scheduler.scheduleWithFixedDelay(() -> {
            try {
                update(port);
            } catch (RuntimeException e) {
                LogUtils.error(TAG, e);
            }
        }, 5000, Settings.DHT_UPDATE_INTERVAL, TimeUnit.MILLISECONDS));

        scheduledActions.add(scheduler.scheduleWithFixedDelay(() -> {
            try {
                long now = System.currentTimeMillis();


                db.expire();
                cache.cleanup(now);
                storage.cleanup();
            } catch (Exception e) {
                LogUtils.error(TAG, e);
            }

        }, 1000, Settings.CHECK_FOR_EXPIRED_ENTRIES, TimeUnit.MILLISECONDS));

        scheduledActions.add(scheduler.scheduleWithFixedDelay(node::decayThrottle, 1, Node.throttleUpdateIntervalMinutes, TimeUnit.MINUTES));

        // single ping to a random node per server to check socket liveness
        scheduledActions.add(scheduler.scheduleWithFixedDelay(() -> {

            for (RPCServer srv : serverManager.getAllServers()) {
                if (srv.getNumActiveRPCCalls() > 0)
                    continue;
                node.getRandomEntry().ifPresent((entry) -> {
                    PingRequest req = new PingRequest();
                    req.setDestination(entry.getAddress());
                    RPCCall call = new RPCCall(req);
                    call.builtFromEntry(entry);
                    call.setExpectedID(entry.getID());
                    srv.doCall(call);
                });
            }
        }, 1, 10, TimeUnit.SECONDS));


        // deep lookup to make ourselves known to random parts of the keyspace
        scheduledActions.add(scheduler.scheduleWithFixedDelay(() -> {
            try {
                for (RPCServer srv : serverManager.getAllServers())
                    findNode(Key.createRandomKey(), false, false, srv, t -> t.setInfo("Random Refresh Lookup"));
            } catch (RuntimeException e) {
                LogUtils.error(TAG, e);
            }


        }, Settings.RANDOM_LOOKUP_INTERVAL, Settings.RANDOM_LOOKUP_INTERVAL, TimeUnit.MILLISECONDS));

        scheduledActions.add(scheduler.scheduleWithFixedDelay(mismatchDetector::purge, 2, 3, TimeUnit.MINUTES));
        scheduledActions.add(scheduler.scheduleWithFixedDelay(unreachableCache::cleanStaleEntries, 2, 3, TimeUnit.MINUTES));
    }


    public void stop() {


        logInfo("Initated DHT shutdown");
        Stream.concat(Arrays.stream(tman.getActiveTasks()), Arrays.stream(tman.getQueuedTasks())).forEach(Task::kill);

        for (ScheduledFuture<?> future : scheduledActions) {
            future.cancel(false);
            // none of the scheduled tasks should experience exceptions, log them if they did
            try {
                future.get();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }


        // scheduler.getQueue().removeAll(scheduledActions);
        scheduledActions.clear();

        logInfo("stopping servers");
        serverManager.destroy();

    }

    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#getNode()
     */
    public Node getNode() {
        return node;
    }

    public IDMismatchDetector getMismatchDetector() {
        return mismatchDetector;
    }

    public Database getDatabase() {
        return db;
    }

    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#getTaskManager()
     */
    public TaskManager getTaskManager() {
        return tman;
    }


    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#update()
     */
    public void update(int port) {

        long now = System.currentTimeMillis();

        serverManager.refresh(now, port);


        node.doBucketChecks(now);

        if (node.getNumEntriesInRoutingTable() < Settings.BOOTSTRAP_IF_LESS_THAN_X_PEERS || now - lastBootstrap > Settings.SELF_LOOKUP_INTERVAL) {
            //regualary search for our id to update routing table
            bootstrap();
        }


    }

    private void resolveBootstrapAddresses() {
        List<InetSocketAddress> nodeAddresses = new ArrayList<>();

        for (InetSocketAddress unres : Settings.UNRESOLVED_BOOTSTRAP_NODES) {
            try {
                for (InetAddress addr : InetAddress.getAllByName(unres.getHostString())) {
                    if (type.canUseAddress(addr))
                        nodeAddresses.add(new InetSocketAddress(addr, unres.getPort()));
                }
            } catch (Exception e) {
                LogUtils.info(TAG, "DNS lookupg for " +
                        unres.getHostString() + "failed: " + e.getMessage());
            }

        }

        // don't overwrite old addresses if DNS fails
        if (!nodeAddresses.isEmpty())
            bootstrapAddresses = nodeAddresses;
    }

    Collection<InetSocketAddress> getBootStrapNodes() {
        return bootstrapAddresses;
    }

    /**
     * Initiates a Bootstrap.
     * <p>
     * This function bootstraps with router.bittorrent.com if there are less
     * than 10 Peers in the routing table. If there are more then a lookup on
     * our own ID is initiated. If the either Task is finished than it will try
     * to fill the Buckets.
     */
    private synchronized void bootstrap() {
        if (System.currentTimeMillis() - lastBootstrap < Settings.BOOTSTRAP_MIN_INTERVAL) {
            return;
        }

        if (!bootstrapping.compareAndSet(BootstrapState.NONE, BootstrapState.FILL))
            return;


        fillHomeBuckets(Collections.emptyList());

    }


    private void fillHomeBuckets(Collection<KBucketEntry> entries) {
        if (node.getNumEntriesInRoutingTable() == 0 && entries.isEmpty()) {
            bootstrapping.set(BootstrapState.NONE);
            return;
        }

        bootstrapping.set(BootstrapState.BOOTSTRAP);

        final AtomicInteger taskCount = new AtomicInteger();

        TaskListener bootstrapListener = t -> {
            int count = taskCount.decrementAndGet();
            if (count == 0) {
                bootstrapping.set(BootstrapState.NONE);
                lastBootstrap = System.currentTimeMillis();
            }

            // fill the remaining buckets once all bootstrap operations finished
            if (count == 0 && node.getNumEntriesInRoutingTable() > Settings.USE_BT_ROUTER_IF_LESS_THAN_X_PEERS) {
                node.fillBuckets();
            }
        };

        for (RPCServer srv : serverManager.getAllServers()) {
            findNode(srv.getDerivedID(), true, true, srv, t -> {
                taskCount.incrementAndGet();
                t.setInfo("Bootstrap: lookup for self");
                t.injectCandidates(entries);
                t.addListener(bootstrapListener);
            });
        }

        if (taskCount.get() == 0)
            bootstrapping.set(BootstrapState.NONE);

    }

    private void findNode(Key id, boolean isBootstrap,
                          boolean isPriority, RPCServer server, Consumer<NodeLookup> configureTask) {
        if (server == null) {
            return;
        }

        NodeLookup at = new NodeLookup(id, server, node, isBootstrap);
        if (configureTask != null)
            configureTask.accept(at);
        tman.addTask(at, isPriority);
    }


    void fillBucket(Key id, KBucket bucket, Consumer<NodeLookup> configure) {
        bucket.updateRefreshTimer();
        findNode(id, false, true, serverManager.getRandomActiveServer(true), configure);
    }


    private void sendError(MessageBase origMsg, int code, String msg) {
        ErrorMessage errMsg = new ErrorMessage(origMsg.getMTID(), code, msg);
        errMsg.setMethod(origMsg.getMethod());
        errMsg.setDestination(origMsg.getOrigin());
        origMsg.getServer().sendMessage(errMsg);
    }


    public enum DHTtype {
        IPV4_DHT("IPv4", 20 + 4 + 2, 4 + 2, Inet4Address.class, 1450, StandardProtocolFamily.INET),
        IPV6_DHT("IPv6", 20 + 16 + 2, 16 + 2, Inet6Address.class, 1200, StandardProtocolFamily.INET6);


        public final int NODES_ENTRY_LENGTH;
        public final int ADDRESS_ENTRY_LENGTH;
        public final Class<? extends InetAddress> PREFERRED_ADDRESS_TYPE;
        public final int MAX_PACKET_SIZE;
        public final ProtocolFamily PROTO_FAMILY;
        final String shortName;

        DHTtype(String shortName, int nodeslength, int addresslength, Class<? extends InetAddress> addresstype, int maxSize, ProtocolFamily family) {

            this.shortName = shortName;
            this.NODES_ENTRY_LENGTH = nodeslength;
            this.PREFERRED_ADDRESS_TYPE = addresstype;
            this.ADDRESS_ENTRY_LENGTH = addresslength;
            this.MAX_PACKET_SIZE = maxSize;
            this.PROTO_FAMILY = family;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean canUseSocketAddress(InetSocketAddress addr) {
            return PREFERRED_ADDRESS_TYPE.isInstance(addr.getAddress());
        }

        public boolean canUseAddress(InetAddress addr) {
            return PREFERRED_ADDRESS_TYPE.isInstance(addr);
        }

    }

    enum BootstrapState {
        NONE,
        BOOTSTRAP,
        FILL
    }

}
