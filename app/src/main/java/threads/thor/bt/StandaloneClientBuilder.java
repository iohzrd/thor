package threads.thor.bt;

import java.util.Objects;
import java.util.function.Supplier;

import threads.thor.bt.data.Storage;
import threads.thor.bt.magnet.MagnetUri;
import threads.thor.bt.magnet.MagnetUriParser;
import threads.thor.bt.processor.ProcessingContext;
import threads.thor.bt.processor.TorrentProcessorFactory;
import threads.thor.bt.processor.listener.ListenerSource;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.processor.magnet.MagnetContext;
import threads.thor.bt.runtime.BtClient;
import threads.thor.bt.runtime.BtRuntime;
import threads.thor.bt.torrent.selector.PieceSelector;
import threads.thor.bt.torrent.selector.RarestFirstSelector;


public class StandaloneClientBuilder {

    private final PieceSelector pieceSelector;
    private Storage storage;
    private MagnetUri magnetUri;
    private BtRuntime runtime;

    public StandaloneClientBuilder() {
        this.pieceSelector = RarestFirstSelector.randomizedRarest();
    }


    public StandaloneClientBuilder storage(Storage storage) {
        this.storage = Objects.requireNonNull(storage, "Missing data storage");
        return (StandaloneClientBuilder) this;
    }


    public StandaloneClientBuilder magnet(String magnetUri) {
        this.magnetUri = MagnetUriParser.lenientParser().parse(magnetUri);
        return (StandaloneClientBuilder) this;
    }


    public StandaloneClientBuilder magnet(MagnetUri magnetUri) {
        this.magnetUri = Objects.requireNonNull(magnetUri, "Missing magnet URI");
        return (StandaloneClientBuilder) this;
    }


    public StandaloneClientBuilder runtime(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "Missing runtime");
        return (StandaloneClientBuilder) this;
    }


    public BtClient build() {
        Objects.requireNonNull(runtime, "Missing runtime");
        Supplier<BtClient> clientSupplier = () -> buildClient(runtime);
        return new LazyClient(clientSupplier);
    }


    private <C extends ProcessingContext> BtClient buildClient(BtRuntime runtime) {

        ListenerSource<MagnetContext> listenerSource = new ListenerSource<>();
        listenerSource.addListener(ProcessingEvent.DOWNLOAD_COMPLETE, (context, next) -> null);

        return new DefaultClient(runtime,
                TorrentProcessorFactory.createMagnetProcessor(runtime),
                new MagnetContext(magnetUri, pieceSelector, storage), listenerSource);
    }

}
