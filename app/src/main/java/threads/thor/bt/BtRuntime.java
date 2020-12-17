package threads.thor.bt;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import threads.LogUtils;
import threads.thor.bt.data.ChunkVerifier;
import threads.thor.bt.data.DataDescriptorFactory;
import threads.thor.bt.data.DataReaderFactory;
import threads.thor.bt.data.DefaultChunkVerifier;
import threads.thor.bt.data.digest.Digester;
import threads.thor.bt.data.digest.JavaSecurityDigester;
import threads.thor.bt.dht.DHTHandshakeHandler;
import threads.thor.bt.dht.DHTPeerSourceFactory;
import threads.thor.bt.dht.MldhtService;
import threads.thor.bt.event.EventBus;
import threads.thor.bt.event.EventSource;
import threads.thor.bt.magnet.UtMetadataMessageHandler;
import threads.thor.bt.net.BitfieldConnectionHandler;
import threads.thor.bt.net.ConnectionHandlerFactory;
import threads.thor.bt.net.ConnectionSource;
import threads.thor.bt.net.DataReceiver;
import threads.thor.bt.net.DataReceivingLoop;
import threads.thor.bt.net.HandshakeHandler;
import threads.thor.bt.net.IConnectionHandlerFactory;
import threads.thor.bt.net.IPeerConnectionFactory;
import threads.thor.bt.net.MessageDispatcher;
import threads.thor.bt.net.PeerConnectionAcceptor;
import threads.thor.bt.net.PeerConnectionFactory;
import threads.thor.bt.net.PeerConnectionPool;
import threads.thor.bt.net.SharedSelector;
import threads.thor.bt.net.SocketChannelConnectionAcceptor;
import threads.thor.bt.net.buffer.BufferManager;
import threads.thor.bt.net.buffer.IBufferManager;
import threads.thor.bt.net.extended.ExtendedProtocolHandshakeHandler;
import threads.thor.bt.net.pipeline.BufferedPieceRegistry;
import threads.thor.bt.net.pipeline.ChannelPipelineFactory;
import threads.thor.bt.net.pipeline.IChannelPipelineFactory;
import threads.thor.bt.net.portmapping.PortMapper;
import threads.thor.bt.net.portmapping.PortMappingInitializer;
import threads.thor.bt.peer.PeerRegistry;
import threads.thor.bt.peerexchange.PeerExchangeConfig;
import threads.thor.bt.peerexchange.PeerExchangeMessageHandler;
import threads.thor.bt.peerexchange.PeerExchangePeerSourceFactory;
import threads.thor.bt.protocol.HandshakeFactory;
import threads.thor.bt.protocol.Message;
import threads.thor.bt.protocol.StandardBittorrentProtocol;
import threads.thor.bt.protocol.extended.AlphaSortedMapping;
import threads.thor.bt.protocol.extended.ExtendedHandshakeFactory;
import threads.thor.bt.protocol.extended.ExtendedMessage;
import threads.thor.bt.protocol.extended.ExtendedMessageTypeMapping;
import threads.thor.bt.protocol.extended.ExtendedProtocol;
import threads.thor.bt.protocol.handler.MessageHandler;
import threads.thor.bt.protocol.handler.PortMessageHandler;
import threads.thor.bt.service.LifecycleBinding;
import threads.thor.bt.service.RuntimeLifecycleBinder;
import threads.thor.bt.torrent.TorrentRegistry;
import threads.thor.bt.torrent.data.BlockCache;
import threads.thor.bt.torrent.data.DataWorker;
import threads.thor.bt.torrent.data.DefaultDataWorker;
import threads.thor.bt.torrent.data.LRUBlockCache;

public class BtRuntime {


    private static final String TAG = BtRuntime.class.getSimpleName();

    public final MessageDispatcher mMessageDispatcher;
    public final ConnectionSource mConnectionSource;
    public final PeerRegistry mPeerRegistry;
    public final TorrentRegistry mTorrentRegistry;
    public final Set<IAgent> mMessagingAgents;
    public final DataWorker mDataWorker;
    public final PeerConnectionPool mConnectionPool;
    //public final TrackerService mTrackerService;
    public final BufferedPieceRegistry mBufferedPieceRegistry;
    private final Object lock;
    private final Config mConfig;
    private final Context mContext;
    private final ExecutorService mExecutor;
    private final EventBus mEventBus;
    private final RuntimeLifecycleBinder mRuntimeLifecycleBinder;
    private final Set<BtClient> knownClients;
    private final AtomicBoolean started;


    public BtRuntime(@NonNull Context context,
                     @NonNull Config config,
                     @NonNull EventBus eventBus) {
        Runtime.getRuntime().addShutdownHook(new Thread("bt.runtime.shutdown-manager") {
            @Override
            public void run() {
                shutdown();
            }
        });
        this.mEventBus = eventBus;
        this.mRuntimeLifecycleBinder = new RuntimeLifecycleBinder();
        new PeerExchangePeerSourceFactory(
                mEventBus, mRuntimeLifecycleBinder, new PeerExchangeConfig());


        this.mConfig = config;
        this.mContext = context;
        this.knownClients = ConcurrentHashMap.newKeySet();

        this.mExecutor = Executors.newSingleThreadExecutor();

        SharedSelector mSelector = provideSelector(mRuntimeLifecycleBinder);

        Digester digester = provideDigester();
        ChunkVerifier chunkVerifier = provideVerifier(eventBus, config, digester);


        DataDescriptorFactory dataDescriptorFactory = provideDataDescriptorFactory(
                config, mEventBus, chunkVerifier);

        this.mTorrentRegistry = new TorrentRegistry(
                dataDescriptorFactory, mRuntimeLifecycleBinder);

        this.mPeerRegistry = new PeerRegistry(mRuntimeLifecycleBinder,
                mTorrentRegistry, mEventBus, config);


        Set<PortMapper> portMappers = new HashSet<>();

        PortMappingInitializer.portMappingInitializer(portMappers, mRuntimeLifecycleBinder, mConfig);


        DataReceiver dataReceiver = new DataReceivingLoop(mSelector, mRuntimeLifecycleBinder);
        BufferManager bufferManager = new BufferManager(config);
        mBufferedPieceRegistry = new BufferedPieceRegistry();
        IChannelPipelineFactory channelPipelineFactory =
                new ChannelPipelineFactory(bufferManager, mBufferedPieceRegistry);

        Set<HandshakeHandler> boundHandshakeHandlers = new HashSet<>();
        Map<String, MessageHandler<? extends ExtendedMessage>> handlersByTypeName = new HashMap<>();
        handlersByTypeName.put("ut_pex", new PeerExchangeMessageHandler());
        handlersByTypeName.put("ut_metadata", new UtMetadataMessageHandler());
        ExtendedMessageTypeMapping messageTypeMapping =
                provideExtendedMessageTypeMapping(handlersByTypeName);

        ExtendedProtocol extendedProtocol = new ExtendedProtocol(messageTypeMapping, handlersByTypeName);
        PortMessageHandler portMessageHandler = new PortMessageHandler();
        Map<Integer, MessageHandler<?>> extraHandlers = new HashMap<>();
        extraHandlers.put(PortMessageHandler.PORT_ID, portMessageHandler);
        extraHandlers.put(ExtendedProtocol.EXTENDED_MESSAGE_ID, extendedProtocol);
        MessageHandler<Message> bittorrentProtocol = new StandardBittorrentProtocol(extraHandlers);

        HandshakeFactory handshakeFactory = new HandshakeFactory(mPeerRegistry);


        ExtendedHandshakeFactory extendedHandshakeFactory = new ExtendedHandshakeFactory(
                mTorrentRegistry,
                messageTypeMapping,
                config);

        IConnectionHandlerFactory connectionHandlerFactory =
                provideConnectionHandlerFactory(handshakeFactory, mTorrentRegistry,
                        boundHandshakeHandlers, extendedHandshakeFactory, config);


        IPeerConnectionFactory peerConnectionFactory = providePeerConnectionFactory(
                mSelector,
                connectionHandlerFactory,
                bittorrentProtocol,
                mTorrentRegistry,
                channelPipelineFactory,
                bufferManager,
                dataReceiver,
                mEventBus,
                config
        );

        mConnectionPool = new PeerConnectionPool(mEventBus,
                mRuntimeLifecycleBinder, config);


        Set<PeerConnectionAcceptor> connectionAcceptors = new HashSet<>();
        connectionAcceptors.add(
                provideSocketChannelConnectionAcceptor(mSelector,
                        peerConnectionFactory, config));


        MldhtService mMldhtService = new MldhtService(mRuntimeLifecycleBinder, mConfig,
                portMappers, mTorrentRegistry, mEventBus);
        DHTHandshakeHandler dHTHandshakeHandler = new DHTHandshakeHandler(mMldhtService.getPort());
        boundHandshakeHandlers.add(dHTHandshakeHandler);

        DHTPeerSourceFactory mDHTPeerSourceFactory =
                new DHTPeerSourceFactory(mRuntimeLifecycleBinder, mMldhtService);
        mPeerRegistry.addPeerSourceFactory(mDHTPeerSourceFactory);


        mDataWorker = provideDataWorker(
                mRuntimeLifecycleBinder, mTorrentRegistry,
                chunkVerifier,
                new LRUBlockCache(mTorrentRegistry, mEventBus),
                config);


        mConnectionSource = new ConnectionSource(connectionAcceptors,
                peerConnectionFactory, mConnectionPool, mRuntimeLifecycleBinder, config);

        this.mMessageDispatcher = new MessageDispatcher(
                mRuntimeLifecycleBinder, mConnectionPool, mTorrentRegistry, config);


        this.mMessagingAgents = new HashSet<>();


        this.started = new AtomicBoolean(false);
        this.lock = new Object();


    }

    private static IConnectionHandlerFactory provideConnectionHandlerFactory(
            HandshakeFactory handshakeFactory, TorrentRegistry torrentRegistry,
            Set<HandshakeHandler> boundHandshakeHandlers,
            ExtendedHandshakeFactory extendedHandshakeFactory, Config config) {

        List<HandshakeHandler> handshakeHandlers = new ArrayList<>(boundHandshakeHandlers);
        // add default handshake handlers to the beginning of the connection handling chain
        handshakeHandlers.add(new BitfieldConnectionHandler(torrentRegistry));
        handshakeHandlers.add(new ExtendedProtocolHandshakeHandler(extendedHandshakeFactory));

        return new ConnectionHandlerFactory(handshakeFactory, torrentRegistry,
                handshakeHandlers, config.getPeerHandshakeTimeout());
    }

    private static ExtendedMessageTypeMapping provideExtendedMessageTypeMapping(
            Map<String, MessageHandler<? extends ExtendedMessage>> handlersByTypeName) {
        return new AlphaSortedMapping(handlersByTypeName);
    }

    private static Digester provideDigester() {
        int step = 2 << 22; // 8 MB
        return new JavaSecurityDigester("SHA-1", step);
    }

    private static ChunkVerifier provideVerifier(EventBus eventBus, Config config, Digester digester) {
        return new DefaultChunkVerifier(eventBus, digester, config.getNumOfHashingThreads());
    }

    private static DataDescriptorFactory provideDataDescriptorFactory(Config config, EventSource eventSource, ChunkVerifier verifier) {
        DataReaderFactory dataReaderFactory = new DataReaderFactory(eventSource);
        return new DataDescriptorFactory(dataReaderFactory, verifier, config.getTransferBlockSize());
    }

    private static DataWorker provideDataWorker(
            RuntimeLifecycleBinder lifecycleBinder,
            TorrentRegistry torrentRegistry,
            ChunkVerifier verifier,
            BlockCache blockCache,
            Config config) {
        return new DefaultDataWorker(lifecycleBinder, torrentRegistry, verifier, blockCache, config.getMaxIOQueueSize());
    }

    public static EventBus provideEventBus() {
        return new EventBus();
    }

    private static SharedSelector provideSelector(RuntimeLifecycleBinder lifecycleBinder) {
        SharedSelector selector;
        try {
            selector = new SharedSelector(Selector.open());
        } catch (IOException e) {
            throw new RuntimeException("Failed to get I/O selector", e);
        }

        Runnable shutdownRoutine = () -> {
            try {
                selector.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close selector", e);
            }
        };
        lifecycleBinder.addBinding(RuntimeLifecycleBinder.LifecycleEvent.SHUTDOWN,
                LifecycleBinding.bind(shutdownRoutine).description("Shutdown selector").build());

        return selector;
    }

    private static IPeerConnectionFactory providePeerConnectionFactory(
            SharedSelector selector,
            IConnectionHandlerFactory connectionHandlerFactory,
            MessageHandler<Message> bittorrentProtocol,
            TorrentRegistry torrentRegistry,
            IChannelPipelineFactory channelPipelineFactory,
            IBufferManager bufferManager,
            DataReceiver dataReceiver,
            EventSource eventSource,
            Config config) {
        return new PeerConnectionFactory(selector, connectionHandlerFactory, channelPipelineFactory,
                bittorrentProtocol, torrentRegistry, bufferManager, dataReceiver, eventSource, config);
    }

    private static SocketChannelConnectionAcceptor provideSocketChannelConnectionAcceptor(
            SharedSelector selector,
            IPeerConnectionFactory connectionFactory,
            Config config) {
        InetSocketAddress localAddress = new InetSocketAddress(config.getAcceptorAddress(), config.getAcceptorPort());
        return new SocketChannelConnectionAcceptor(selector, connectionFactory, localAddress);
    }


    public ExecutorService getExecutor() {
        return mExecutor;
    }

    public EventBus getEventBus() {
        return mEventBus;
    }


    /**
     * @return Runtime configuration
     * @since 1.0
     */
    public Config getConfig() {
        return mConfig;
    }

    public Context getContext() {
        return mContext;
    }


    /**
     * @return true if this runtime is up and running
     * @since 1.0
     */
    public boolean isRunning() {
        return started.get();
    }

    public void startup() {
        if (started.compareAndSet(false, true)) {
            synchronized (lock) {
                runHooks(RuntimeLifecycleBinder.LifecycleEvent.STARTUP, e -> LogUtils.error(TAG, "Error on runtime startup", e));
            }
        }
    }


    public void attachClient(BtClient client) {
        knownClients.add(client);
    }

    public void detachClient(BtClient client) {
        if (knownClients.remove(client)) {
            if (knownClients.isEmpty()) {
                shutdown();
            }
        } else {
            throw new IllegalArgumentException("Unknown client: " + client);
        }
    }


    /**
     * Manually initiate the runtime shutdown procedure, which includes:
     * - stopping all attached clients
     * - stopping all workers and executors, that were created inside this runtime
     *
     * @since 1.0
     */
    private void shutdown() {
        if (started.compareAndSet(true, false)) {
            synchronized (lock) {
                knownClients.forEach(client -> {
                    try {
                        client.stop();
                    } catch (Throwable e) {
                        LogUtils.error(TAG, "Error when stopping client", e);
                    }
                });

                runHooks(RuntimeLifecycleBinder.LifecycleEvent.SHUTDOWN, this::onShutdownHookError);
                mExecutor.shutdownNow();
            }
        }
    }

    private void runHooks(RuntimeLifecycleBinder.LifecycleEvent event, Consumer<Throwable> errorConsumer) {
        ExecutorService executor = createLifecycleExecutor(event);

        Map<LifecycleBinding, CompletableFuture<Void>> futures = new HashMap<>();
        List<LifecycleBinding> syncBindings = new ArrayList<>();

        mRuntimeLifecycleBinder.visitBindings(
                event,
                binding -> {
                    if (binding.isAsync()) {
                        futures.put(binding, CompletableFuture.runAsync(toRunnable(binding), executor));
                    } else {
                        syncBindings.add(binding);
                    }
                });

        syncBindings.forEach(binding -> {
            String errorMessage = createErrorMessage(event, binding);
            try {
                toRunnable(binding).run();
            } catch (Throwable e) {
                errorConsumer.accept(new BtException(errorMessage, e));
            }
        });

        // if the app is shutting down, then we must wait for the futures to complete
        if (event == RuntimeLifecycleBinder.LifecycleEvent.SHUTDOWN) {
            futures.forEach((binding, future) -> {
                String errorMessage = createErrorMessage(event, binding);
                try {
                    future.get(mConfig.getShutdownHookTimeout().toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    errorConsumer.accept(new BtException(errorMessage, e));
                }
            });
        }

        shutdownGracefully(executor);
    }

    private String createErrorMessage(RuntimeLifecycleBinder.LifecycleEvent event, LifecycleBinding binding) {
        Optional<String> descriptionOptional = binding.getDescription();
        String errorMessage = "Failed to execute " + event.name().toLowerCase() + " hook: ";
        errorMessage += ": " + (descriptionOptional.orElseGet(() -> binding.getRunnable().toString()));
        return errorMessage;
    }

    private ExecutorService createLifecycleExecutor(RuntimeLifecycleBinder.LifecycleEvent event) {
        AtomicInteger threadCount = new AtomicInteger();
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "bt.runtime." + event.name().toLowerCase() + "-worker-" + threadCount.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private void shutdownGracefully(ExecutorService executor) {
        executor.shutdown();
        try {
            long timeout = mConfig.getShutdownHookTimeout().toMillis();
            boolean terminated = executor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
            if (!terminated) {
                LogUtils.warning(TAG, "Failed to shutdown executor in {} millis");
            }
        } catch (InterruptedException e) {
            // ignore

            executor.shutdownNow();
        }
    }

    private Runnable toRunnable(LifecycleBinding binding) {
        return () -> {
            Runnable r = binding.getRunnable();

            Optional<String> descriptionOptional = binding.getDescription();
            descriptionOptional.orElseGet(r::toString);

            r.run();
        };
    }

    private void onShutdownHookError(Throwable e) {
        // logging facilities might be unavailable at this moment,
        // so using standard output
        e.printStackTrace(System.err);
        System.err.flush();
    }
}
