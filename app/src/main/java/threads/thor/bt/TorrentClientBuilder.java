package threads.thor.bt;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import threads.thor.bt.data.Storage;
import threads.thor.bt.magnet.MagnetUri;
import threads.thor.bt.magnet.MagnetUriParser;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.processor.ProcessingContext;
import threads.thor.bt.processor.ProcessingStage;
import threads.thor.bt.processor.listener.ListenerSource;
import threads.thor.bt.processor.listener.ProcessingEvent;
import threads.thor.bt.processor.magnet.MagnetContext;
import threads.thor.bt.runtime.BtRuntime;
import threads.thor.bt.torrent.fileselector.TorrentFileSelector;
import threads.thor.bt.torrent.selector.PieceSelector;
import threads.thor.bt.torrent.selector.RarestFirstSelector;


public class TorrentClientBuilder<B extends TorrentClientBuilder> extends BaseClientBuilder<B> {

    private final PieceSelector pieceSelector;
    private Storage storage;
    private MagnetUri magnetUri;
    private TorrentFileSelector fileSelector;
    private List<Consumer<Torrent>> torrentConsumers;
    private List<Runnable> fileSelectionListeners;

    private boolean stopWhenDownloaded;

    /**
     * @since 1.4
     */
    TorrentClientBuilder() {
        // set default piece selector
        this.pieceSelector = RarestFirstSelector.randomizedRarest();
    }

    /**
     * Set the provided storage as the data back-end
     *
     * @since 1.4
     */
    @SuppressWarnings("unchecked")
    public B storage(Storage storage) {
        this.storage = Objects.requireNonNull(storage, "Missing data storage");
        return (B) this;
    }

    /**
     * Set magnet URI in BEP-9 format
     *
     * @param magnetUri Magnet URI
     * @see MagnetUriParser
     * @since 1.4
     */
    @SuppressWarnings("unchecked")
    public B magnet(String magnetUri) {
        this.magnetUri = MagnetUriParser.lenientParser().parse(magnetUri);
        return (B) this;
    }


    public B magnet(MagnetUri magnetUri) {
        this.magnetUri = Objects.requireNonNull(magnetUri, "Missing magnet URI");
        return (B) this;
    }


    public B stopWhenDownloaded() {
        this.stopWhenDownloaded = true;
        return (B) this;
    }


    @Override
    protected ProcessingContext buildProcessingContext(BtRuntime runtime) {
        Objects.requireNonNull(storage, "Missing data storage");
        return new MagnetContext(magnetUri, pieceSelector, storage);
    }

    @Override
    protected <C extends ProcessingContext> void collectStageListeners(ListenerSource<C> listenerSource) {
        if (torrentConsumers != null && torrentConsumers.size() > 0) {
            BiFunction<C, ProcessingStage<C>, ProcessingStage<C>> listener = (context, next) -> {
                context.getTorrent().ifPresent(torrent -> {
                    for (Consumer<Torrent> torrentConsumer : torrentConsumers) {
                        torrentConsumer.accept(torrent);
                    }
                });
                return next;
            };
            listenerSource.addListener(ProcessingEvent.TORRENT_FETCHED, listener);
        }

        if (fileSelectionListeners != null && fileSelectionListeners.size() > 0) {
            BiFunction<C, ProcessingStage<C>, ProcessingStage<C>> listener = (context, next) -> {
                fileSelectionListeners.forEach(Runnable::run);
                return next;
            };
            listenerSource.addListener(ProcessingEvent.FILES_CHOSEN, listener);
        }

        if (stopWhenDownloaded) {
            listenerSource.addListener(ProcessingEvent.DOWNLOAD_COMPLETE, (context, next) -> null);
        }
    }

}
