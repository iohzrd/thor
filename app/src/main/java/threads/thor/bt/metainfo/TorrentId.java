package threads.thor.bt.metainfo;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

import threads.thor.bt.BtException;
import threads.thor.bt.protocol.Protocols;

public class TorrentId {

    private static final int TORRENT_ID_LENGTH = 20;
    private final byte[] torrentId;

    private TorrentId(byte[] torrentId) {
        Objects.requireNonNull(torrentId);
        if (torrentId.length != TORRENT_ID_LENGTH) {
            throw new BtException("Illegal threads.torrent ID length: " + torrentId.length);
        }
        this.torrentId = torrentId;
    }

    public static int length() {
        return TORRENT_ID_LENGTH;
    }


    public static TorrentId fromBytes(byte[] bytes) {
        return new TorrentId(bytes);
    }

    public byte[] getBytes() {
        return torrentId;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(torrentId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !TorrentId.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        return Arrays.equals(torrentId, ((TorrentId) obj).getBytes());
    }

    @NonNull
    @Override
    public String toString() {
        return Protocols.toHex(torrentId);
    }
}
