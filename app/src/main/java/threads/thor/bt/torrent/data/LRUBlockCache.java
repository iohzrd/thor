/*
 * Copyright (c) 2016—2019 Andrei Tomashpolskiy and individual contributors.
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

package threads.thor.bt.torrent.data;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import threads.thor.bt.data.DataRange;
import threads.thor.bt.event.EventSource;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.torrent.TorrentRegistry;

public class LRUBlockCache implements BlockCache {
    private static final int MIN_SLOTS_COUNT = 20;

    private final TorrentRegistry torrentRegistry;
    private final Map<TorrentId, ByteBuffer> buffers;
    private final Map<TorrentId, List<Slot>> lruSlots;
    private final Map<TorrentId, Map<Integer, Slot>> slotByPieceIndexMap;


    public LRUBlockCache(TorrentRegistry torrentRegistry, EventSource eventSource) {
        this.torrentRegistry = torrentRegistry;
        this.buffers = new HashMap<>();
        this.lruSlots = new HashMap<>();
        this.slotByPieceIndexMap = new HashMap<>();

        eventSource.onTorrentStarted(e -> initializeBuffer(e.getTorrentId()));
        eventSource.onTorrentStopped(e -> releaseBuffer(e.getTorrentId()));
    }

    private synchronized void initializeBuffer(TorrentId torrentId) {
        if (buffers.containsKey(torrentId)) {
            throw new IllegalStateException("Buffer already exists for threads.torrent ID: " + torrentId);
        }

        Torrent torrent = torrentRegistry.getTorrent(torrentId).get();

        int chunkSize = (int) torrent.getChunkSize();
        int chunksCount = torrentRegistry.getDescriptor(torrentId).get()
                .getDataDescriptor()
                .getChunkDescriptors().size();
        int slotsCount = Math.min(chunksCount, MIN_SLOTS_COUNT);
        int bufferSize = chunkSize * slotsCount;

        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        buffers.put(torrentId, buffer);
        lruSlots.put(torrentId, createSlots(buffer, chunkSize));
        slotByPieceIndexMap.put(torrentId, new HashMap<>());

    }

    private List<Slot> createSlots(ByteBuffer buffer, int chunkSize) {
        List<Slot> slots = new LinkedList<>();
        for (int i = 0; i < buffer.capacity() / chunkSize; i++) {
            buffer.limit(chunkSize * (i + 1));
            buffer.position(chunkSize * i);
            slots.add(new Slot(i, buffer.slice()));
        }
        return slots;
    }

    private synchronized void releaseBuffer(TorrentId torrentId) {
        buffers.remove(torrentId);
        lruSlots.remove(torrentId);
        slotByPieceIndexMap.remove(torrentId);

    }

    @Override
    public synchronized BlockReader get(TorrentId torrentId, int pieceIndex, int offset, int length) {
        DataRange data = torrentRegistry.getDescriptor(torrentId).get()
                .getDataDescriptor()
                .getChunkDescriptors().get(pieceIndex)
                .getData();

        ByteBuffer buffer = buffers.get(torrentId);
        if (buffer == null) {
            throw new IllegalStateException("Missing buffer for threads.torrent ID: " + torrentId);
        }

        Slot slot = tryGetSlot(torrentId, pieceIndex);
        if (slot == null) {
            slot = tryClaimSlot(torrentId, pieceIndex);
            if (slot == null) {

                return buffer1 -> {
                    int bufferRemaining = buffer1.remaining();
                    if (!data.getSubrange(offset, length)
                            .getBytes(buffer1)) {
                        throw new IllegalStateException("Failed to read data to buffer:" +
                                " piece index {" + pieceIndex + "}," +
                                " offset {" + offset + "}," +
                                " length: {" + length + "}," +
                                " buffer space {" + bufferRemaining + "}");
                    }
                    return true;
                };
            }
            if (!data.getBytes(slot.buffer)) {
                throw new IllegalStateException("Failed to load data into buffer slot:" +
                        "threads.torrent ID {" + torrentId + "}, piece index {" + pieceIndex + "}, slot {" + slot.buffer + "}");
            }
        }

        return readFromSlot(slot, offset, length);
    }

    @Nullable
    private Slot tryGetSlot(TorrentId torrentId, int pieceIndex) {
        Map<Integer, Slot> mapping = slotByPieceIndexMap.get(torrentId);
        Objects.requireNonNull(mapping);
        Slot slot = mapping.get(pieceIndex);
        if (slot != null) {

            List<Slot> lruSlots = this.lruSlots.get(torrentId);
            Objects.requireNonNull(lruSlots);
            Iterator<Slot> iterate = lruSlots.iterator();
            while (iterate.hasNext()) {
                Slot lruSlot = iterate.next();
                if (lruSlot.index == slot.index) {
                    iterate.remove();
                    lruSlots.add(lruSlot);
                    break;
                }
            }
            return slot;
        }
        return null;
    }

    @Nullable
    private Slot tryClaimSlot(TorrentId torrentId, int pieceIndex) {
        List<Slot> slots = lruSlots.get(torrentId);
        Objects.requireNonNull(slots);
        Iterator<Slot> iter = slots.iterator();
        Slot slot;
        while (iter.hasNext()) {
            slot = iter.next();
            if (slot.currentUsers == 0) {
                iter.remove();
                slot.buffer.clear();
                slots.add(slot);
                Objects.requireNonNull(slotByPieceIndexMap.get(torrentId)).put(pieceIndex, slot);

                return slot;
            }
        }

        return null;
    }

    private BlockReader readFromSlot(Slot slot, int offset, int length) {
        slot.currentUsers += 1;

        slot.buffer.limit(offset + length);
        slot.buffer.position(offset);
        ByteBuffer block = slot.buffer.slice();

        return buffer -> {
            synchronized (LRUBlockCache.this) {
                try {
                    if (buffer.remaining() < block.remaining()) {
                        return false;
                    }
                    buffer.put(block);
                    block.clear();
                    return true;
                } finally {
                    int usedBy1 = slot.currentUsers -= 1;
                }
            }
        };
    }

    private static class Slot {
        private final int index;
        private final ByteBuffer buffer;
        private int currentUsers;

        private Slot(int index, ByteBuffer buffer) {
            this.index = index;
            this.buffer = buffer;
            this.currentUsers = 0;
        }
    }
}
