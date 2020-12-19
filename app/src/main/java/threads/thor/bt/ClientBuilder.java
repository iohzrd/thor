package threads.thor.bt;

import java.util.Objects;

import threads.thor.bt.data.Storage;
import threads.thor.bt.magnet.MagnetUri;
import threads.thor.bt.magnet.MagnetUriParser;
import threads.thor.bt.processor.ListenerSource;
import threads.thor.bt.processor.MagnetContext;
import threads.thor.bt.processor.ProcessingEvent;
import threads.thor.bt.processor.TorrentProcessorFactory;
import threads.thor.bt.torrent.PieceSelector;
import threads.thor.bt.torrent.RarestFirstSelector;


public class ClientBuilder {

    private final PieceSelector pieceSelector;
    private Storage storage;
    private MagnetUri magnetUri;
    private Runtime runtime;

    public ClientBuilder() {
        this.pieceSelector = RarestFirstSelector.randomizedRarest();
    }


    public ClientBuilder storage(Storage storage) {
        this.storage = Objects.requireNonNull(storage, "Missing data storage");
        return this;
    }


    public ClientBuilder magnet(String magnetUri) {
        this.magnetUri = MagnetUriParser.lenientParser().parse(magnetUri);
        return this;
    }


    public ClientBuilder magnet(MagnetUri magnetUri) {
        this.magnetUri = Objects.requireNonNull(magnetUri, "Missing magnet URI");
        return this;
    }


    public ClientBuilder runtime(Runtime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "Missing runtime");
        return this;
    }


    public Client build() {
        Objects.requireNonNull(runtime, "Missing runtime");
        ListenerSource listenerSource = new ListenerSource();
        listenerSource.addListener(ProcessingEvent.DOWNLOAD_COMPLETE, (context, next) -> null);

        return new Client(runtime,
                TorrentProcessorFactory.createMagnetProcessor(runtime),
                new MagnetContext(magnetUri, pieceSelector, storage), listenerSource);
    }


}
