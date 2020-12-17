package threads.thor.bt.metainfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import threads.LogUtils;
import threads.thor.bt.BtException;


public final class Torrent {
    private static final String TAG = Torrent.class.getSimpleName();

    private static final int CHUNK_HASH_LENGTH = 20;

    private final TorrentSource source;

    private final TorrentId torrentId;
    private final String name;
    private final long chunkSize;
    private final byte[] chunkHashes;
    private final long size;
    private final List<TorrentFile> files = new ArrayList<>();
    private long creationDate;
    private boolean isPrivate;
    private String createdBy;
    private String comment;


    private Torrent(@NonNull TorrentId torrentId, @NonNull String name,
                    @NonNull TorrentSource source, @NonNull List<TorrentFile> torrentFiles,
                    byte[] chunkHashes, long size, long chunkSize) {
        this.torrentId = torrentId;
        this.name = name;
        this.source = source;
        this.creationDate = System.currentTimeMillis();
        this.chunkHashes = chunkHashes;
        this.size = size;
        this.chunkSize = chunkSize;
        this.files.addAll(torrentFiles);
    }

    public static Torrent createTorrent(@NonNull TorrentId torrentId,
                                        @NonNull String name,
                                        @NonNull TorrentSource source,
                                        @NonNull List<TorrentFile> torrentFiles,
                                        byte[] chunkHashes,
                                        long size,
                                        long chunkSize) {

        if (chunkHashes.length % CHUNK_HASH_LENGTH != 0) {
            throw new BtException("Invalid chunk hashes string -- length (" + chunkHashes.length
                    + ") is not divisible by " + CHUNK_HASH_LENGTH);
        }
        if (torrentFiles.isEmpty()) {
            TorrentFile file = new TorrentFile(size);

            // TODO: Name can be missing according to the spec,
            // so need to make sure that it's present
            // (probably by setting it to a user-defined value after processing the threads.torrent metainfo)
            file.setPathElements(Collections.singletonList(name));
            torrentFiles.add(file);
        }

        Torrent torrent = new Torrent(torrentId, name, source, torrentFiles, chunkHashes, size, chunkSize);
        torrent.build();
        LogUtils.info(TAG, torrent.toString());
        return torrent;
    }

    @Override
    public String toString() {
        return "Torrent{" +
                "source=" + source +
                ", torrentId=" + torrentId +
                ", name='" + name + '\'' +
                ", chunkSize=" + chunkSize +
                ", chunkHashes=" + Arrays.toString(chunkHashes) +
                ", size=" + size +
                ", files=" + files +
                ", creationDate=" + creationDate +
                ", isPrivate=" + isPrivate +
                ", createdBy='" + createdBy + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Torrent that = (Torrent) o;
        return chunkSize == that.chunkSize &&
                size == that.size &&
                isPrivate == that.isPrivate &&
                Objects.equals(torrentId, that.torrentId) &&
                Objects.equals(name, that.name) &&
                Arrays.equals(chunkHashes, that.chunkHashes) &&
                Objects.equals(files, that.files) &&
                Objects.equals(creationDate, that.creationDate) &&
                Objects.equals(createdBy, that.createdBy);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(torrentId, name, chunkSize, size, files, isPrivate, creationDate, createdBy);
        result = 31 * result + Arrays.hashCode(chunkHashes);
        return result;
    }

    public TorrentSource getSource() {
        return source;
    }

    public TorrentId getTorrentId() {
        return torrentId;
    }

    public String getName() {
        return name;
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public byte[] getChunkHashes() {
        return chunkHashes;
    }

    public Iterable<byte[]> getIterableChunkHashes() {

        return () -> new Iterator<byte[]>() {

            private int read;

            @Override
            public boolean hasNext() {
                return read < chunkHashes.length;
            }

            @Override
            public byte[] next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int start = read;
                read += CHUNK_HASH_LENGTH;
                return Arrays.copyOfRange(chunkHashes, start, read);
            }
        };
    }

    public long getSize() {
        return size;
    }

    public List<TorrentFile> getFiles() {
        return Collections.unmodifiableList(files);
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(long creationDate) {
        this.creationDate = creationDate;
    }

    @Nullable
    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(@Nullable String createdBy) {
        this.createdBy = createdBy;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    public void setComment(@Nullable String comment) {
        this.comment = comment;
    }

    private void build() {

        Map<Long, TorrentFile> ranges = new HashMap<>();
        List<TorrentFile> files = getFiles();
        long startRange = 0L;
        for (TorrentFile file : files) {

            LogUtils.info(TAG, "start " + startRange + " " + file.toString());
            ranges.put(startRange, file);
            startRange += file.getSize();
        }

        long totalSize = getSize();
        long chunkSize = getChunkSize();


        int chunks = 0;
        long off, lim;
        long remaining = totalSize;
        while (remaining > 0) {
            off = chunks * chunkSize;
            lim = Math.min(chunkSize, remaining);

            List<TorrentFile> chunkFiles = new ArrayList<>();
            for (long start : ranges.keySet()) {
                if (start <= (lim + off)) {
                    TorrentFile entry = ranges.get(start);
                    Objects.requireNonNull(entry);
                    if (off <= start + entry.getSize()) {
                        chunkFiles.add(entry);
                    }
                }
            }
            for (TorrentFile tf : chunkFiles) {
                tf.addPiece(chunks);
            }

            chunks++;

            remaining -= chunkSize;
        }
    }
}
