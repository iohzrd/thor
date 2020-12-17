package threads.thor.bt;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import threads.thor.bt.torrent.TorrentSessionState;

class LazyClient implements BtClient {

    private final Supplier<BtClient> clientSupplier;
    private volatile BtClient delegate;


    LazyClient(Supplier<BtClient> clientSupplier) {
        this.clientSupplier = clientSupplier;
    }

    private synchronized void initClient() {
        if (delegate == null) {
            delegate = clientSupplier.get();
        }
    }

    @Override
    public CompletableFuture<?> startAsync() {
        if (delegate == null) {
            initClient();
        }
        return delegate.startAsync();
    }

    @Override
    public CompletableFuture<?> startAsync(Consumer<TorrentSessionState> listener, long period) {
        if (delegate == null) {
            initClient();
        }
        return delegate.startAsync(listener, period);
    }

    @Override
    public void stop() {
        if (delegate == null) {
            return;
        }
        delegate.stop();
    }

    @Override
    public boolean isStarted() {
        return delegate != null && delegate.isStarted();
    }
}
