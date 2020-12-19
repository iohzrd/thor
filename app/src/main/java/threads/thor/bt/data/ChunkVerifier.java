package threads.thor.bt.data;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import threads.thor.bt.data.digest.JavaSecurityDigester;
import threads.thor.bt.event.EventBus;
import threads.thor.bt.metainfo.TorrentId;

public class ChunkVerifier {

    private final JavaSecurityDigester digester;
    private final int numOfHashingThreads;
    private final EventBus eventBus;

    public ChunkVerifier(EventBus eventBus, JavaSecurityDigester digester, int numOfHashingThreads) {
        this.eventBus = eventBus;
        this.digester = digester;
        this.numOfHashingThreads = numOfHashingThreads;
    }


    public void verify(TorrentId torrentId, List<ChunkDescriptor> chunks, Bitfield bitfield) {
        if (chunks.size() != bitfield.getPiecesTotal()) {
            throw new IllegalArgumentException("Bitfield has different size than the list of chunks. Bitfield size: " +
                    bitfield.getPiecesTotal() + ", number of chunks: " + chunks.size());
        }

        ChunkDescriptor[] arr = chunks.toArray(new ChunkDescriptor[chunks.size()]);
        if (numOfHashingThreads > 1) {
            collectParallel(torrentId, arr, bitfield);
        } else {
            createWorker(torrentId, arr, 0, arr.length, bitfield).run();
        }
        // try to purge all data that was loaded by the verifiers
        System.gc();

    }


    public boolean verify(ChunkDescriptor chunk) {
        byte[] expected = chunk.getChecksum();
        byte[] actual = digester.digestForced(chunk.getData());
        return Arrays.equals(expected, actual);
    }


    public boolean verifyIfPresent(ChunkDescriptor chunk) {
        byte[] expected = chunk.getChecksum();
        byte[] actual = digester.digest(chunk.getData());
        return Arrays.equals(expected, actual);
    }

    private void collectParallel(TorrentId torrentId, ChunkDescriptor[] chunks, Bitfield bitfield) {
        int n = numOfHashingThreads;
        ExecutorService workers = Executors.newFixedThreadPool(n);

        List<Future<?>> futures = new ArrayList<>();

        int batchSize = chunks.length / n;
        int i, limit = 0;
        while ((i = limit) < chunks.length) {
            if (futures.size() == n - 1) {
                // assign the remaining bits to the last worker
                limit = chunks.length;
            } else {
                limit = i + batchSize;
            }
            futures.add(workers.submit(createWorker(torrentId, chunks, i, Math.min(chunks.length, limit), bitfield)));
        }


        Set<Throwable> errors = ConcurrentHashMap.newKeySet();
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                errors.add(e);
            }
        });

        workers.shutdown();
        while (!workers.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpectedly interrupted");
            }
        }

        if (!errors.isEmpty()) {
            throw new RuntimeException("Failed to verify threads.torrent data:" +
                    errors.stream().map(this::errorToString).reduce(String::concat).get());
        }

    }

    private Runnable createWorker(TorrentId torrentId,
                                  ChunkDescriptor[] chunks,
                                  int from,
                                  int to,
                                  Bitfield bitfield) {
        return () -> {
            int i = from;
            while (i < to) {
                // optimization to speedup the initial verification of threads.torrent's data
                int[] emptyUnits = new int[]{0};
                chunks[i].getData().visitUnits((u, off, lim) -> {
                    // limit of 0 means an empty file,
                    // and we don't want to account for those
                    if (u.size() == 0 && lim != 0) {
                        emptyUnits[0]++;
                    }
                });

                // if any of this chunk's storage units is empty,
                // then the chunk is neither complete nor verified
                if (emptyUnits[0] == 0) {
                    boolean verified = verifyIfPresent(chunks[i]);
                    if (verified) {
                        bitfield.markVerified(i);
                        eventBus.firePieceVerified(torrentId, i);
                    }
                }
                i++;
            }
        };
    }

    private String errorToString(Throwable e) {
        StringBuilder buf = new StringBuilder();
        buf.append("\n");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bos);
        e.printStackTrace(out);

        buf.append(bos.toString());
        return buf.toString();
    }
}
