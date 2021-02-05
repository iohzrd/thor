package threads.thor.magnet;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import threads.thor.magnet.processor.ChainProcessor;
import threads.thor.magnet.processor.ListenerSource;
import threads.thor.magnet.processor.MagnetContext;
import threads.thor.magnet.torrent.TorrentSessionState;

public class Client {

    private final Runtime runtime;
    private final ChainProcessor processor;
    private final ListenerSource listenerSource;
    private final MagnetContext context;

    private volatile CompletableFuture<?> futureOptional;
    private volatile Consumer<TorrentSessionState> listenerOptional;

    private volatile ScheduledExecutorService listenerExecutor;

    Client(Runtime runtime,
           ChainProcessor processor,
           MagnetContext context,
           ListenerSource listenerSource) {
        this.runtime = runtime;
        this.processor = processor;
        this.context = context;
        this.listenerSource = listenerSource;

        this.futureOptional = null;
        this.listenerOptional = null;
    }


    public synchronized CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
        if (futureOptional != null) {
            throw new RuntimeException("Can't start -- already running");
        }

        this.listenerExecutor = Executors.newSingleThreadScheduledExecutor();
        this.listenerOptional = listener;

        listenerExecutor.scheduleAtFixedRate(this::notifyListener, period, period, TimeUnit.MILLISECONDS);

        return doStartAsync();
    }

    private void notifyListener() {
        if (listenerOptional != null) {
            listenerOptional.accept(context.getState());
        }
    }

    private void shutdownListener() {
        listenerExecutor.shutdownNow();
    }

    private CompletableFuture<?> doStartAsync() {
        ensureRuntimeStarted();
        attachToRuntime();

        CompletableFuture<?> future = processor.process(context, listenerSource);

        future.whenComplete((r, t) -> notifyListener())
                .whenComplete((r, t) -> shutdownListener())
                .whenComplete((r, t) -> stop());

        this.futureOptional = future;

        return future;
    }

    public synchronized void stop() {
        // order is important (more precisely, unsetting futureOptional BEFORE completing the future)
        // to prevent attempt to detach the client after it has already been detached once
        // (may happen when #stop() is called from the outside)
        if (futureOptional != null) {
            CompletableFuture<?> f = futureOptional;
            futureOptional = null;
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

}
