package threads.thor.utils;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import threads.LogUtils;
import threads.thor.bt.data.StorageUnit;
import threads.thor.bt.event.PieceVerifiedEvent;
import threads.thor.bt.metainfo.TorrentFile;
import threads.thor.bt.net.buffer.ByteBufferView;
import threads.thor.ipfs.IPFS;


class ContentStorageUnit implements StorageUnit, Consumer<PieceVerifiedEvent> {

    private static final String TAG = ContentStorageUnit.class.getSimpleName();

    private final File file;
    private final DocumentFile doc;
    private final long capacity;
    private final Set<Integer> pieces = new HashSet<>();
    private final ContentStorage contentStorage;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private SeekableByteChannel sbc;
    private volatile boolean closed;

    ContentStorageUnit(@NonNull ContentStorage contentStorage, @NonNull TorrentFile torrentFile,
                       @NonNull DocumentFile doc) {

        this.doc = doc;
        this.contentStorage = contentStorage;
        String ident = String.join("", torrentFile.getPathElements());

        this.file = new File(contentStorage.getDataDir(), "bt" + ident.hashCode() + ".bt");
        this.capacity = torrentFile.getSize();
        this.closed = true;
        init();
        this.closed = false;
        this.pieces.addAll(torrentFile.getPieces());
        long piecesSize = pieces.size();


        if (piecesSize > 0) {
            contentStorage.getEventBus().onPieceVerified(this);
        }


        LogUtils.info(TAG, "Paths : " + torrentFile.getPathElements().toString());
        LogUtils.info(TAG, "Pieces : " + torrentFile.getPieces().size());

    }

    @NonNull
    File getFile() {
        return file;
    }

    public boolean isFinished() {
        return finished.get();
    }

    private void finalizeStream() {


        try (FileInputStream fis = new FileInputStream(file)) {
            Objects.requireNonNull(fis);
            Uri uri = doc.getUri();
            try (OutputStream os = contentStorage.getContext().
                    getContentResolver().openOutputStream(uri)) {
                Objects.requireNonNull(os);
                IPFS.copy(fis, os);
            }
            finished.set(true);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    private void init() {
        if (!file.exists()) {
            try {
                boolean success = file.createNewFile();
                if (!success) {
                    LogUtils.error(TAG, "File couldn't be created");
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }
        }

        LogUtils.info(TAG, "File Size : " + file.length());

        try {
            sbc = Files.newByteChannel(file.toPath(),
                    StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
        } catch (IOException e) {
            throw new UncheckedIOException("Unexpected I/O error", e);
        }
    }

    @Override
    public synchronized int readBlock(ByteBuffer buffer, long offset) {
        if (closed) {
            return -1;
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to read past the end of file (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            sbc.position(offset);
            return sbc.read(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read bytes (offset: " + offset +
                    ", requested block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized void readBlockFully(ByteBuffer buffer, long offset) {
        int read = 0, total = 0;
        do {
            total += read;
            read = readBlock(buffer, offset + total);
        } while (read >= 0 && buffer.hasRemaining());
    }

    private synchronized int writeBlock(ByteBuffer buffer, long offset) {
        if (closed) {
            return -1;
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            sbc.position(offset);
            return sbc.write(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write bytes (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized void writeBlockFully(ByteBuffer buffer, long offset) {
        int written = 0, total = 0;
        do {
            total += written;
            written = writeBlock(buffer, offset + total);
        } while (written >= 0 && buffer.hasRemaining());
    }

    private synchronized int writeBlock(ByteBufferView buffer, long offset) {
        if (closed) {
            return -1;
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Negative offset: " + offset);
        } else if (offset > capacity - buffer.remaining()) {
            throw new IllegalArgumentException("Received a request to write past the end of file (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity);
        }

        try {
            sbc.position(offset);
            return buffer.transferTo(sbc);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write bytes (offset: " + offset +
                    ", block length: " + buffer.remaining() + ", file capacity: " + capacity + ")", e);
        }
    }

    @Override
    public synchronized void writeBlockFully(ByteBufferView buffer, long offset) {
        int written = 0, total = 0;
        do {
            total += written;
            written = writeBlock(buffer, offset + total);
        } while (written >= 0 && buffer.hasRemaining());

    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public long size() {
        return file.length();
    }

    @NonNull
    @Override
    public String toString() {
        return "(" + size() + " of " + capacity + " B) ";
    }

    @Override
    public void close() {

        LogUtils.info(TAG, "close " + toString());
        if (!closed) {
            try {
                sbc.close();
            } catch (IOException e) {
                LogUtils.error(TAG, e);
            } finally {
                closed = true;
            }
        }
    }

    public boolean isComplete() {
        return pieces.isEmpty();
    }

    @Override
    public void accept(PieceVerifiedEvent pieceVerifiedEvent) {
        int piece = pieceVerifiedEvent.getPieceIndex();
        boolean removed = pieces.remove(piece);
        if (removed) {

            if (isComplete()) {
                boolean rm = contentStorage.getEventBus().removePieceVerified(this);
                if (!rm) {
                    LogUtils.error(TAG, "PieceVerifiedEventListener not removed");
                }
                finalizeStream();
            }
        }
    }
}