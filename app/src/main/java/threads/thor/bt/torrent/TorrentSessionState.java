/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.torrent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import threads.thor.bt.net.ConnectionKey;
import threads.thor.bt.torrent.messaging.ConnectionState;
import threads.thor.bt.torrent.messaging.TorrentWorker;

import static java.util.stream.Collectors.summingLong;

public final class TorrentSessionState {

    /**
     * Recently calculated amounts of downloaded and uploaded data
     */
    private final Map<ConnectionKey, TransferAmounts> recentAmountsForConnectedPeers;

    /**
     * Historical data (amount of data downloaded from disconnected peers)
     */
    private final AtomicLong downloadedFromDisconnected;

    /**
     * Historical data (amount of data uploaded to disconnected peers)
     */
    private final AtomicLong uploadedToDisconnected;

    private final TorrentDescriptor descriptor;
    private final TorrentWorker worker;

    public TorrentSessionState(TorrentDescriptor descriptor, TorrentWorker worker) {
        this.recentAmountsForConnectedPeers = new HashMap<>();
        this.downloadedFromDisconnected = new AtomicLong();
        this.uploadedToDisconnected = new AtomicLong();
        this.descriptor = descriptor;
        this.worker = worker;
    }

    public int getPiecesTotal() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesTotal();
        } else {
            return 1;
        }
    }


    public int getPiecesComplete() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesComplete();
        } else {
            return 0;
        }
    }


    public int getPiecesIncomplete() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesIncomplete();
        } else {
            return 1;
        }
    }


    public int getPiecesRemaining() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesRemaining();
        } else {
            return 1;
        }
    }


    public int getPiecesSkipped() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesSkipped();
        } else {
            return 0;
        }
    }

    public int getPiecesNotSkipped() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesNotSkipped();
        } else {
            return 1;
        }
    }

    public synchronized long getDownloaded() {
        long downloaded = getCurrentAmounts().values().stream().collect(summingLong(TransferAmounts::getDownloaded));
        downloaded += downloadedFromDisconnected.get();
        return downloaded;
    }

    public synchronized long getUploaded() {
        long uploaded = getCurrentAmounts().values().stream().collect(summingLong(TransferAmounts::getUploaded));
        uploaded += uploadedToDisconnected.get();
        return uploaded;
    }

    private synchronized Map<ConnectionKey, TransferAmounts> getCurrentAmounts() {
        Map<ConnectionKey, TransferAmounts> connectedPeers = getAmountsForConnectedPeers();

        Set<ConnectionKey> disconnectedPeers = new HashSet<>();
        recentAmountsForConnectedPeers.forEach((peer, amounts) -> {
            if (!connectedPeers.containsKey(peer)) {
                downloadedFromDisconnected.addAndGet(amounts.getDownloaded());
                uploadedToDisconnected.addAndGet(amounts.getUploaded());
                disconnectedPeers.add(peer);
            }
        });
        recentAmountsForConnectedPeers.keySet().removeAll(disconnectedPeers);

        recentAmountsForConnectedPeers.putAll(connectedPeers);

        return recentAmountsForConnectedPeers;
    }

    private Map<ConnectionKey, TransferAmounts> getAmountsForConnectedPeers() {
        return worker.getPeers().stream()
                .collect(
                        HashMap::new,
                        (acc, peer) -> {
                            ConnectionState connectionState = worker.getConnectionState(peer);
                            acc.put(
                                    peer,
                                    new TransferAmounts(connectionState.getDownloaded(), connectionState.getUploaded())
                            );
                        },
                        HashMap::putAll);
    }

    public Set<ConnectionKey> getConnectedPeers() {
        return Collections.unmodifiableSet(worker.getPeers());
    }

    private static class TransferAmounts {
        private final long downloaded;
        private final long uploaded;

        TransferAmounts(long downloaded, long uploaded) {
            this.downloaded = downloaded;
            this.uploaded = uploaded;
        }

        long getDownloaded() {
            return downloaded;
        }

        long getUploaded() {
            return uploaded;
        }
    }
}
