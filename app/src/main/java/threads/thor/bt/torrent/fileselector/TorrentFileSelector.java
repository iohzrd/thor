package threads.thor.bt.torrent.fileselector;

import java.util.List;
import java.util.stream.Collectors;

import threads.thor.bt.metainfo.TorrentFile;

public abstract class TorrentFileSelector {


    public List<SelectionResult> selectFiles(List<TorrentFile> files) {
        return files.stream().map(this::select).collect(Collectors.toList());
    }

    protected abstract SelectionResult select(TorrentFile file);
}
