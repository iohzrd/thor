package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
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
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import threads.LogUtils;
import threads.thor.bt.DHTConfiguration;
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
import threads.thor.bt.kad.utils.AddressUtils;
import threads.thor.bt.kad.utils.ByteWrapper;
import threads.thor.bt.kad.utils.PopulationEstimator;
import threads.thor.bt.utils.NIOConnectionManager;

import static threads.thor.bt.bencode.Utils.prettyPrint;
import static threads.thor.bt.utils.Functional.awaitAll;

/**
 * @author Damokles
 */
public class DHT implements DHTBase {

    private static final String TAG = DHT.class.getSimpleName();
    private volatile static ScheduledThreadPoolExecutor defaultScheduler;
    private static ThreadGroup executorGroup;


    private final DHTtype type;
    private final Consumer<RPCServer> serverListener;
    private final RPCCallListener rpcListener;
    private final AtomicReference<BootstrapState> bootstrapping = new AtomicReference<>(BootstrapState.NONE);
    private final List<DHTStatsListener> statsListeners;
    private final List<DHTStatusListener> statusListeners;
    private final List<DHTIndexingListener> indexingListeners;
    private final DHTStats stats;
    private final PopulationEstimator estimator;
    private final List<ScheduledFuture<?>> scheduledActions = new ArrayList<>();
    private final List<DHT> siblingGroup = new ArrayList<>();
    private final List<IncomingMessageListener> incomingMessageListeners = new ArrayList<>();
    DHTConfiguration config;
    RPCStats serverStats;
    private IDMismatchDetector mismatchDetector;
    private NonReachableCache unreachableCache;
    private NIOConnectionManager connectionManager;
    private boolean running;
    private long lastBootstrap;
    private Node node;
    private RPCServerManager serverManager;
    private GenericStorage storage;
    private Database db;
    private TaskManager tman;
    private boolean useRouterBootstrapping;
    private DHTStatus status;
    private AnnounceNodeCache cache;
    private ScheduledExecutorService scheduler;
    private Collection<InetSocketAddress> bootstrapAddresses = Collections.emptyList();

    public DHT(@NonNull DHTtype type) {
        this.type = type;

        siblingGroup.add(this);
        stats = new DHTStats();
        status = DHTStatus.Stopped;
        statsListeners = new ArrayList<>(2);
        statusListeners = new ArrayList<>(2);
        indexingListeners = new ArrayList<>();
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

        serverListener = (srv) -> {
            node.registerServer(srv);

            srv.onEnqueue((c) -> c.addListener(rpcListener));
        };

    }


    /**
     * @return the scheduler
     */
    private static ScheduledExecutorService getDefaultScheduler() {
        ScheduledExecutorService service = defaultScheduler;
        if (service == null) {
            initDefaultScheduler();
            service = defaultScheduler;
        }

        return service;
    }

    private static void initDefaultScheduler() {
        synchronized (DHT.class) {
            if (defaultScheduler == null) {
                executorGroup = new ThreadGroup("mlDHT");
                int threads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
                defaultScheduler = new ScheduledThreadPoolExecutor(threads, r -> {
                    Thread t = new Thread(executorGroup, r, "mlDHT Scheduler");

                    t.setUncaughtExceptionHandler((t1, e) -> DHT.log(e));
                    t.setDaemon(true);
                    return t;
                });
                defaultScheduler.setCorePoolSize(threads);
                defaultScheduler.setKeepAliveTime(20, TimeUnit.SECONDS);
                defaultScheduler.allowCoreThreadTimeOut(true);
            }
        }
    }


    public static void log(Throwable e) {
        LogUtils.error(TAG, e);
    }

    public static void logError(String message) {
        LogUtils.error(TAG, message);
    }

    static void logInfo(String message) {
        LogUtils.info(TAG, message);
    }


    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    private Optional<DHT> getSiblingByType(DHTtype type) {
        return siblingGroup.stream().filter(sib -> sib.getType() == type).findAny();
    }

    public List<DHT> getSiblings() {
        return Collections.unmodifiableList(siblingGroup);
    }


    void incomingMessage(MessageBase msg) {
        incomingMessageListeners.forEach(e -> e.received(this, msg));
    }

    public void ping(PingRequest r) {
        if (!isRunning()) {
            return;
        }

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
        if (!isRunning()) {
            return;
        }

        // ignore requests we get from ourself
        if (node.isLocalId(r.getID())) {
            return;
        }

        AbstractLookupResponse response;
        if (r instanceof FindNodeRequest)
            response = new FindNodeResponse(r.getMTID());
        else
            response = new UnknownTypeResponse(r.getMTID());

        populateResponse(r.getTarget(), response, r.doesWant4() ? DHTConstants.MAX_ENTRIES_PER_BUCKET : 0, r.doesWant6() ? DHTConstants.MAX_ENTRIES_PER_BUCKET : 0);

        response.setDestination(r.getOrigin());
        r.getServer().sendMessage(response);

        node.recieved(r);
    }

    private void populateResponse(Key target, AbstractLookupResponse rsp, int v4, int v6) {
        if (v4 > 0) {
            getSiblingByType(DHTtype.IPV4_DHT).filter(DHT::isRunning).ifPresent(sib -> {
                KClosestNodesSearch kns = new KClosestNodesSearch(target, v4, sib);
                kns.fill(DHTtype.IPV4_DHT == type);
                rsp.setNodes(kns.asNodeList());
            });
        }

        if (v6 > 0) {
            getSiblingByType(DHTtype.IPV6_DHT).filter(DHT::isRunning).ifPresent(sib -> {
                KClosestNodesSearch kns = new KClosestNodesSearch(target, v6, sib);
                kns.fill(DHTtype.IPV6_DHT == type);
                rsp.setNodes(kns.asNodeList());
            });
        }
    }

    public void response(MessageBase r) {
        if (!isRunning()) {
            return;
        }

        node.recieved(r);
    }

    public void get(GetRequest req) {
        if (!isRunning()) {
            return;
        }

        GetResponse rsp = new GetResponse(req.getMTID());

        populateResponse(req.getTarget(), rsp, req.doesWant4() ?
                DHTConstants.MAX_ENTRIES_PER_BUCKET : 0, req.doesWant6() ?
                DHTConstants.MAX_ENTRIES_PER_BUCKET : 0);

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
        if (!isRunning()) {
            return;
        }

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

        for (DHTIndexingListener listener : indexingListeners) {
            List<PeerAddressDBItem> toAdd = listener.incomingPeersRequest(r.getInfoHash(), r.getOrigin().getAddress(), r.getID());
            if (dbl == null && !toAdd.isEmpty())
                dbl = new ArrayList<>();
            if (dbl != null && !toAdd.isEmpty())
                dbl.addAll(toAdd);
        }

        // generate a token
        ByteWrapper token = null;
        if (db.insertForKeyAllowed(r.getInfoHash()))
            token = db.genToken(r.getID(), r.getOrigin().getAddress(), r.getOrigin().getPort(), r.getInfoHash());

        int want4 = r.doesWant4() ? DHTConstants.MAX_ENTRIES_PER_BUCKET : 0;
        int want6 = r.doesWant6() ? DHTConstants.MAX_ENTRIES_PER_BUCKET : 0;

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
        if (!isRunning()) {
            return;
        }

        // ignore requests we get from ourself
        if (node.isLocalId(r.getID())) {
            return;
        }

        // first check if the token is OK
        ByteWrapper token = new ByteWrapper(r.getToken());
        if (!db.checkToken(token, r.getID(), r.getOrigin().getAddress(), r.getOrigin().getPort(), r.getInfoHash())) {
            sendError(r, ErrorCode.ProtocolError.code, "Invalid Token; tokens expire after " + DHTConstants.TOKEN_TIMEOUT + "ms; only valid for the IP/port to which it was issued; only valid for the infohash for which it was issued");
            return;
        }

        // everything OK, so store the value
        PeerAddressDBItem item = PeerAddressDBItem.createFromAddress(r.getOrigin().getAddress(), r.getPort(), r.isSeed());
        r.getVersion().ifPresent(item::setVersion);
        if (config.noRouterBootstrap() || !AddressUtils.isBogon(item))
            db.store(r.getInfoHash(), item);

        // send a proper response to indicate everything is OK
        AnnounceResponse rsp = new AnnounceResponse(r.getMTID());
        rsp.setDestination(r.getOrigin());
        r.getServer().sendMessage(rsp);

        node.recieved(r);
    }

    public void sample(SampleRequest r) {
        if (!isRunning())
            return;

        SampleResponse rsp = new SampleResponse(r.getMTID());
        rsp.setSamples(db.samples());
        rsp.setDestination(r.getOrigin());
        rsp.setNum(db.getStats().getKeyCount());
        rsp.setInterval((int) TimeUnit.MILLISECONDS.toSeconds(DHTConstants.CHECK_FOR_EXPIRED_ENTRIES));
        populateResponse(r.getTarget(), rsp, r.doesWant4() ? DHTConstants.MAX_ENTRIES_PER_BUCKET : 0, r.doesWant6() ? DHTConstants.MAX_ENTRIES_PER_BUCKET : 0);

        r.getServer().sendMessage(rsp);

        node.recieved(r);
    }

    public void error(ErrorMessage r) {
        StringBuilder b = new StringBuilder();
        b.append("Error [").append(r.getCode()).append("] from: ").append(r.getOrigin());
        b.append(" Message: \"").append(r.getMessage()).append("\"");
        r.getVersion().ifPresent(v -> b.append(" version:").append(prettyPrint(v)));

        DHT.logError(b.toString());
    }

    public void timeout(RPCCall r) {
        if (isRunning()) {
            node.onTimeout(r);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#addDHTNode(java.lang.String, int)
     */
    public void addDHTNode(String host, int hport) {
        if (!isRunning()) {
            return;
        }
        InetSocketAddress addr = new InetSocketAddress(host, hport);

        if (!addr.isUnresolved() && (config.noRouterBootstrap() || !AddressUtils.isBogon(addr))) {
            if (!type.PREFERRED_ADDRESS_TYPE.isInstance(addr.getAddress()) || node.getNumEntriesInRoutingTable() > DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS)
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
        if (!isRunning()) {
            return null;
        }
        Key id = new Key(info_hash);

        RPCServer srv = serverManager.getRandomActiveServer(false);
        if (srv == null)
            return null;

        return new PeerLookupTask(srv, node, id);
    }

    public AnnounceTask announce(PeerLookupTask lookup, boolean isSeed, int btPort) {
        if (!isRunning()) {
            return null;
        }

        // reuse the same server to make sure our tokens are still valid
        AnnounceTask announce = new AnnounceTask(lookup.getRPC(), node, lookup.getInfoHash(), btPort, lookup.getAnnounceCanidates());
        announce.setSeed(isSeed);

        tman.addTask(announce);

        return announce;
    }

    public DHTConfiguration getConfig() {
        return config;
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

    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#getStats()
     */
    public DHTStats getStats() {
        return stats;
    }

    /**
     * @return the status
     */
    public DHTStatus getStatus() {
        return status;
    }

    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#isRunning()
     */
    public boolean isRunning() {
        return running;
    }

    private int getPort() {
        int port = config.getListeningPort();
        if (port < 1 || port > 65535)
            port = 49001;
        return port;
    }

    private void populate() {
        serverStats = new RPCStats();


        cache = new AnnounceNodeCache();
        stats.setRpcStats(serverStats);

        serverManager = new RPCServerManager(this);
        mismatchDetector = new IDMismatchDetector(this);
        node = new Node(this);
        unreachableCache = new NonReachableCache();

        serverManager.notifyOnServerAdded(serverListener);
        db = new Database();
        stats.setDbStats(db.getStats());
        tman = new TaskManager(this);
        running = true;
        storage = new GenericStorage();
    }

    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#start(java.lang.String, int)
     */
    public void start(@NonNull DHTConfiguration config) {
        if (running) {
            return;
        }

        if (this.scheduler == null)
            this.scheduler = getDefaultScheduler(); // TODO make not static
        this.config = config;
        useRouterBootstrapping = !config.noRouterBootstrap();

        setStatus(DHTStatus.Stopped, DHTStatus.Initializing);
        stats.resetStartedTimestamp();

        logInfo("Starting DHT on port " + getPort());

        // we need the IPs to filter bootstrap nodes out from the routing table. but don't block startup on DNS resolution
        scheduler.execute(this::resolveBootstrapAddresses);

        connectionManager = new NIOConnectionManager("mlDHT " + type.shortName + " NIO Selector");

        populate();


        node.initKey(config);


        // these checks are fairly expensive on large servers (network interface enumeration)
        // schedule them separately
        scheduledActions.add(scheduler.scheduleWithFixedDelay(serverManager::doBindChecks, 10, 10, TimeUnit.SECONDS));

        scheduledActions.add(scheduler.scheduleWithFixedDelay(() -> {
            // maintenance that should run all the time, before the first queries
            tman.dequeue();

            if (running)
                onStatsUpdate();
        }, 5000, DHTConstants.DHT_UPDATE_INTERVAL, TimeUnit.MILLISECONDS));

        // initialize as many RPC servers as we need
        serverManager.refresh(System.currentTimeMillis());

        if (serverManager.getServerCount() == 0) {
            logError("No network interfaces eligible for DHT sockets found during startup."
                    + "\nAddress family: " + this.getType()
                    + "\nmultihoming [requires public IP addresses if enabled]: " + config.allowMultiHoming()
                    + "\nPublic IP addresses: " + AddressUtils.getAvailableGloballyRoutableAddrs(getType().PREFERRED_ADDRESS_TYPE)
                    + "\nDefault route: " + AddressUtils.getDefaultRoute(getType().PREFERRED_ADDRESS_TYPE));
        }

        started();

    }

    public void started() {

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
                update();
            } catch (RuntimeException e) {
                LogUtils.error(TAG, e);
            }
        }, 5000, DHTConstants.DHT_UPDATE_INTERVAL, TimeUnit.MILLISECONDS));

        scheduledActions.add(scheduler.scheduleWithFixedDelay(() -> {
            try {
                long now = System.currentTimeMillis();


                db.expire();
                cache.cleanup(now);
                storage.cleanup();
            } catch (Exception e) {
                LogUtils.error(TAG, e);
            }

        }, 1000, DHTConstants.CHECK_FOR_EXPIRED_ENTRIES, TimeUnit.MILLISECONDS));

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
            } catch (RuntimeException e1) {
                LogUtils.error(TAG, e1);
            }


        }, DHTConstants.RANDOM_LOOKUP_INTERVAL, DHTConstants.RANDOM_LOOKUP_INTERVAL, TimeUnit.MILLISECONDS));

        scheduledActions.add(scheduler.scheduleWithFixedDelay(mismatchDetector::purge, 2, 3, TimeUnit.MINUTES));
        scheduledActions.add(scheduler.scheduleWithFixedDelay(unreachableCache::cleanStaleEntries, 2, 3, TimeUnit.MINUTES));
    }


    public void stop() {
        if (!running) {
            return;
        }

        logInfo("Initated DHT shutdown");
        Stream.concat(Arrays.stream(tman.getActiveTasks()), Arrays.stream(tman.getQueuedTasks())).forEach(Task::kill);

        for (ScheduledFuture<?> future : scheduledActions) {
            future.cancel(false);
            // none of the scheduled tasks should experience exceptions, log them if they did
            try {
                future.get();
            } catch (ExecutionException | InterruptedException e) {
                LogUtils.error(TAG, e);
            } catch (CancellationException e) {
                // do nothing, we just cancelled it
            }
        }


        // scheduler.getQueue().removeAll(scheduledActions);
        scheduledActions.clear();

        logInfo("stopping servers");
        running = false;
        serverManager.destroy();

        stopped();
        tman = null;
        db = null;
        node = null;
        cache = null;
        serverManager = null;
        setStatus(DHTStatus.Initializing, DHTStatus.Stopped);
        setStatus(DHTStatus.Running, DHTStatus.Stopped);
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
     * @see threads.thor.bt.kad.DHTBase#stopped()
     */
    public void stopped() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     *
     * @see threads.thor.bt.kad.DHTBase#update()
     */
    public void update() {

        long now = System.currentTimeMillis();

        serverManager.refresh(now);

        if (!isRunning()) {
            return;
        }

        node.doBucketChecks(now);

        if (node.getNumEntriesInRoutingTable() < DHTConstants.BOOTSTRAP_IF_LESS_THAN_X_PEERS || now - lastBootstrap > DHTConstants.SELF_LOOKUP_INTERVAL) {
            //regualary search for our id to update routing table
            bootstrap();
        } else {
            setStatus(DHTStatus.Initializing, DHTStatus.Running);
        }


    }

    private void resolveBootstrapAddresses() {
        List<InetSocketAddress> nodeAddresses = new ArrayList<>();

        for (InetSocketAddress unres : DHTConstants.UNRESOLVED_BOOTSTRAP_NODES) {
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
        if (!isRunning() || System.currentTimeMillis() - lastBootstrap < DHTConstants.BOOTSTRAP_MIN_INTERVAL) {
            return;
        }

        if (!bootstrapping.compareAndSet(BootstrapState.NONE, BootstrapState.FILL))
            return;

        if (useRouterBootstrapping && node.getNumEntriesInRoutingTable() < DHTConstants.USE_BT_ROUTER_IF_LESS_THAN_X_PEERS) {
            routerBootstrap();
        } else {
            fillHomeBuckets(Collections.emptyList());
        }
    }

    private void routerBootstrap() {

        List<CompletableFuture<RPCCall>> callFutures = new ArrayList<>();

        resolveBootstrapAddresses();
        Collection<InetSocketAddress> addrs = bootstrapAddresses;

        for (InetSocketAddress addr : addrs) {
            if (!type.canUseSocketAddress(addr))
                continue;
            FindNodeRequest fnr = new FindNodeRequest(Key.createRandomKey());
            fnr.setDestination(addr);
            RPCCall c = new RPCCall(fnr);
            CompletableFuture<RPCCall> f = new CompletableFuture<>();

            RPCServer srv = serverManager.getRandomActiveServer(true);
            if (srv == null)
                continue;

            c.addListener(new RPCCallListener() {
                @Override
                public void stateTransition(RPCCall c, RPCState previous, RPCState current) {
                    if (current == RPCState.RESPONDED || current == RPCState.ERROR || current == RPCState.TIMEOUT)
                        f.complete(c);
                }
            });
            callFutures.add(f);
            srv.doCall(c);
        }

        awaitAll(callFutures).thenAccept(calls -> {
            Class<FindNodeResponse> clazz = FindNodeResponse.class;
            Set<KBucketEntry> s = calls.stream().filter(clazz::isInstance)
                    .map(clazz::cast).map(fnr -> fnr.getNodes(getType()))
                    .flatMap(NodeList::entries).collect(Collectors.toSet());
            fillHomeBuckets(s);
        });

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
            if (count == 0 && running && node.getNumEntriesInRoutingTable() > DHTConstants.USE_BT_ROUTER_IF_LESS_THAN_X_PEERS) {
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
        if (!running || server == null) {
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

    public Key getOurID() {
        if (running) {
            return node.getRootID();
        }
        return null;
    }

    private void onStatsUpdate() {
        stats.setNumTasks(tman.getNumTasks() + tman.getNumQueuedTasks());
        stats.setNumPeers(node.getNumEntriesInRoutingTable());
        long numSent = 0;
        long numReceived = 0;
        int activeCalls = 0;
        for (RPCServer s : serverManager.getAllServers()) {
            numSent += s.getNumSent();
            numReceived += s.getNumReceived();
            activeCalls += s.getNumActiveRPCCalls();
        }
        stats.setNumSentPackets(numSent);
        stats.setNumReceivedPackets(numReceived);
        stats.setNumRpcCalls(activeCalls);

        for (int i = 0; i < statsListeners.size(); i++) {
            statsListeners.get(i).statsUpdated(stats);
        }
    }

    private void setStatus(DHTStatus expected, DHTStatus newStatus) {
        if (this.status.equals(expected)) {
            DHTStatus old = this.status;
            this.status = newStatus;
            if (!statusListeners.isEmpty()) {
                for (int i = 0; i < statusListeners.size(); i++) {
                    statusListeners.get(i).statusChanged(newStatus, old);
                }
            }
        }
    }

    public void addStatsListener(DHTStatsListener listener) {
        statsListeners.add(listener);
    }

    public void removeStatsListener(DHTStatsListener listener) {
        statsListeners.remove(listener);
    }


    public void printDiagnostics(PrintWriter w) {
        if (!running)
            return;


        for (ScheduledFuture<?> f : scheduledActions)
            if (f.isDone()) { // check for exceptions
                try {
                    f.get();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace(w);
                }

            }


        w.println("==========================");
        w.println("DHT Diagnostics. Type " + type);
        w.println("# of active servers / all servers: " + serverManager.getActiveServerCount() + '/' + serverManager.getServerCount());

        if (!isRunning())
            return;

        w.append("-----------------------\n");
        w.append("Stats\n");
        w.append("Reachable node estimate: " + estimator.getEstimate() + " (" + estimator.getStability() + ")\n");
        w.append(stats.toString());
        w.append("-----------------------\n");
        w.append("Routing table\n");
        try {
            node.buildDiagnistics(w);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        w.append('\n');
        w.append("-----------------------\n");
        w.append("RPC Servers\n");
        for (RPCServer srv : serverManager.getAllServers())
            w.append(srv.toString());
        w.append("-----------------------\n");
        w.append("Blacklist\n");
        w.append(mismatchDetector.toString() + '\n');
        w.append("-----------------------\n");
        w.append("Lookup Cache\n");
        cache.printDiagnostics(w);
        w.append("-----------------------\n");
        w.append("Tasks\n");
        w.append(tman.toString());
        w.append("\n\n\n");
    }

    public enum DHTtype {
        IPV4_DHT("IPv4", 20 + 4 + 2, 4 + 2, Inet4Address.class, 20 + 8, 1450, StandardProtocolFamily.INET),
        IPV6_DHT("IPv6", 20 + 16 + 2, 16 + 2, Inet6Address.class, 40 + 8, 1200, StandardProtocolFamily.INET6);

        public final int HEADER_LENGTH;
        public final int NODES_ENTRY_LENGTH;
        public final int ADDRESS_ENTRY_LENGTH;
        public final Class<? extends InetAddress> PREFERRED_ADDRESS_TYPE;
        public final int MAX_PACKET_SIZE;
        public final ProtocolFamily PROTO_FAMILY;
        final String shortName;

        DHTtype(String shortName, int nodeslength, int addresslength, Class<? extends InetAddress> addresstype, int header, int maxSize, ProtocolFamily family) {

            this.shortName = shortName;
            this.NODES_ENTRY_LENGTH = nodeslength;
            this.PREFERRED_ADDRESS_TYPE = addresstype;
            this.ADDRESS_ENTRY_LENGTH = addresslength;
            this.HEADER_LENGTH = header;
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


    interface IncomingMessageListener {
        void received(DHT dht, MessageBase msg);
    }
}
