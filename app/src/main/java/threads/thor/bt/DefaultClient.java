package threads.thor.bt;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import threads.thor.bt.processor.ListenerSource;
import threads.thor.bt.processor.MagnetContext;
import threads.thor.bt.processor.Processor;
import threads.thor.bt.torrent.TorrentSessionState;

class DefaultClient implements BtClient {

    private final BtRuntime runtime;
    private final Processor<MagnetContext> processor;
    private final ListenerSource<MagnetContext> listenerSource;
    private final MagnetContext context;

    private volatile Optional<CompletableFuture<?>> futureOptional;
    private volatile Optional<Consumer<TorrentSessionState>> listenerOptional;

    private volatile ScheduledExecutorService listenerExecutor;

    DefaultClient(BtRuntime runtime,
                  Processor<MagnetContext> processor,
                  MagnetContext context,
                  ListenerSource<MagnetContext> listenerSource) {
        this.runtime = runtime;
        this.processor = processor;
        this.context = context;
        this.listenerSource = listenerSource;

        this.futureOptional = Optional.empty();
        this.listenerOptional = Optional.empty();
    }

    @Override
    public synchronized CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
        if (futureOptional.isPresent()) {
            throw new BtException("Can't start -- already running");
        }

        this.listenerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.listenerOptional = Optional.of(listener);

        listenerExecutor.scheduleAtFixedRate(this::notifyListener, period, period, TimeUnit.MILLISECONDS);

        return doStartAsync();
    }

    private void notifyListener() {
        listenerOptional.ifPresent(listener ->
                context.getState().ifPresent(listener));
    }

    private void shutdownListener() {
        listenerExecutor.shutdownNow();
    }

    @Override
    public synchronized CompletableFuture<?> startAsync() {
        if (futureOptional.isPresent()) {
            throw new BtException("Can't start -- already running");
        }

        return doStartAsync();
    }

    private CompletableFuture<?> doStartAsync() {
        ensureRuntimeStarted();
        attachToRuntime();

        CompletableFuture<?> future = processor.process(context, listenerSource);

        future.whenComplete((r, t) -> notifyListener())
                .whenComplete((r, t) -> shutdownListener())
                .whenComplete((r, t) -> stop());

        this.futureOptional = Optional.of(future);

        return future;
    }

    @Override
    public synchronized void stop() {
        // order is important (more precisely, unsetting futureOptional BEFORE completing the future)
        // to prevent attempt to detach the client after it has already been detached once
        // (may happen when #stop() is called from the outside)
        if (futureOptional.isPresent()) {
            CompletableFuture<?> f = futureOptional.get();
            futureOptional = Optional.empty();
            detachFromRuntime();
            f.complete(null);
        }
    }

    private void ensureRuntimeStarted() {
        if (!runtime.isRunning()) {
            runtime.startup();
        }
    }

    private void attachToRuntime() {
        runtime.attachClient(this);
    }

    private void detachFromRuntime() {
        runtime.detachClient(this);
    }

    @Override
    public synchronized boolean isStarted() {
        return futureOptional.isPresent();
    }
}
