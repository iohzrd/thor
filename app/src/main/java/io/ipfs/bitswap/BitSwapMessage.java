package io.ipfs.bitswap;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.ipfs.cid.Cid;
import io.ipfs.cid.Prefix;
import io.ipfs.format.BasicBlock;
import io.ipfs.format.Block;
import io.ipfs.multihash.Multihash;
import io.protos.bitswap.BitswapProtos;

public interface BitSwapMessage {


    static int BlockPresenceSize(@NonNull Cid c) {
        return BitswapProtos.Message.BlockPresence.newBuilder()
                .setCid(ByteString.copyFrom(c.Bytes()))
                .setType(BitswapProtos.Message.BlockPresenceType.Have).build().getSerializedSize();
    }


    static BitSwapMessage New(boolean full) {
        return new BitSwapMessageImpl(full);
    }


    static BitSwapMessage newMessageFromProto(BitswapProtos.Message pbm) {
        BitSwapMessageImpl m = new BitSwapMessageImpl(pbm.getWantlist().getFull());
        for (BitswapProtos.Message.Wantlist.Entry e :
                pbm.getWantlist().getEntriesList()) {
            Cid cid = new Cid(e.getBlock().toByteArray());
            if (!cid.Defined()) {
                throw new RuntimeException("errCidMissing");
            }
            m.addEntry(cid, e.getPriority(), e.getCancel(), e.getWantType(), e.getSendDontHave());
        }
        // deprecated
        for (ByteString data : pbm.getBlocksList()) {
            // CIDv0, sha256, protobuf only (TODO check again)
            Block block = BasicBlock.NewBlock(data.toByteArray());
            m.AddBlock(block);
        }
        for (io.protos.bitswap.BitswapProtos.Message.Block b : pbm.getPayloadList()) {
            ByteString prefix = b.getPrefix();
            Prefix pref = Prefix.PrefixFromBytes(prefix.toByteArray());
            Cid cid = pref.Sum(b.getData().toByteArray());
            Block block = BasicBlock.NewBlockWithCid(cid, b.getData().toByteArray());
            m.AddBlock(block);
        }

        for (io.protos.bitswap.BitswapProtos.Message.BlockPresence bi : pbm.getBlockPresencesList()) {
            Cid cid = new Cid(bi.getCid().toByteArray());
            if (!cid.Defined()) {
                throw new RuntimeException("errCidMissing");
            }
            m.AddBlockPresence(cid, bi.getType());
        }


        m.pendingBytes = pbm.getPendingBytes();

        return m;
    }

    static BitSwapMessage fromData(byte[] data) throws InvalidProtocolBufferException {
        BitswapProtos.Message message = BitswapProtos.Message.parseFrom(data);
        return newMessageFromProto(message);
    }

    // Wantlist returns a slice of unique keys that represent data wanted by
    // the sender.
    List<Entry> Wantlist();

    // Blocks returns a slice of unique blocks.
    List<Block> Blocks();

    // BlockPresences returns the list of HAVE / DONT_HAVE in the message
    List<BlockPresence> BlockPresences();

    // Haves returns the Cids for each HAVE
    List<Cid> Haves();

    // DontHaves returns the Cids for each DONT_HAVE
    List<Cid> DontHaves();

    // PendingBytes returns the number of outstanding bytes of data that the
    // engine has yet to send to the client (because they didn't fit in this
    // message)
    int PendingBytes();

    // AddEntry adds an entry to the Wantlist.
    int AddEntry(@NonNull Cid key, int priority, @NonNull BitswapProtos.Message.Wantlist.WantType wantType, boolean sendDontHave);

    // Cancel adds a CANCEL for the given CID to the message
    // Returns the size of the CANCEL entry in the protobuf
    int Cancel(@NonNull Cid key);

    // Remove removes any entries for the given CID. Useful when the want
    // status for the CID changes when preparing a message.
    void Remove(@NonNull Cid key);

    // Empty indicates whether the message has any information
    boolean Empty();

    // Size returns the size of the message in bytes
    int Size();

    // A full wantlist is an authoritative copy, a 'non-full' wantlist is a patch-set
    boolean Full();

    // AddBlock adds a block to the message
    void AddBlock(@NonNull Block block);

    // AddBlockPresence adds a HAVE / DONT_HAVE for the given Cid to the message
    void AddBlockPresence(@NonNull Cid cid, @NonNull BitswapProtos.Message.BlockPresenceType type);

    // AddHave adds a HAVE for the given Cid to the message
    void AddHave(@NonNull Cid cid);

    // AddDontHave adds a DONT_HAVE for the given Cid to the message
    void AddDontHave(@NonNull Cid cid);

    // SetPendingBytes sets the number of bytes of data that are yet to be sent
    // to the client (because they didn't fit in this message)
    void SetPendingBytes(int pendingBytes);


    byte[] ToNetV1();

    // Reset the values in the message back to defaults, so it can be reused
    void Reset(boolean reset);

    // Clone the message fields
    BitSwapMessage Clone();


    // Entry is a wantlist entry in a Bitswap message, with flags indicating
// - whether message is a cancel
// - whether requester wants a DONT_HAVE message
// - whether requester wants a HAVE message (instead of the block)
    class Entry extends io.ipfs.bitswap.Entry {
        public boolean Cancel;
        public boolean SendDontHave;


        // Get the size of the entry on the wire
        public int Size() {
            BitswapProtos.Message.Wantlist.Entry epb = ToPB();
            return epb.getSerializedSize();
        }

        // Get the entry in protobuf form
        public BitswapProtos.Message.Wantlist.Entry ToPB() {

            // TODO check if Cid is correct
            return BitswapProtos.Message.Wantlist.Entry.newBuilder().setBlock(
                    ByteString.copyFrom(Cid.Bytes())
            ).setPriority(Priority).setCancel(Cancel).setWantType(WantType).setSendDontHave(SendDontHave).build();

        }
    }


    // BitSwapMessage is the basic interface for interacting building, encoding,
    // and decoding messages sent on the BitSwap protocol.
    // BlockPresence represents a HAVE / DONT_HAVE for a given Cid
    class BlockPresence {
        public Cid Cid;
        public BitswapProtos.Message.BlockPresenceType Type;
    }

    class BitSwapMessageImpl implements BitSwapMessage {

        private static final String TAG = BitSwapMessage.class.getSimpleName();
        boolean full;
        final HashMap<Cid, Entry> wantlist = new HashMap<>();
        final HashMap<Cid, Block> blocks = new HashMap<>();
        final HashMap<Cid, BitswapProtos.Message.BlockPresenceType> blockPresences = new HashMap<>();
        int pendingBytes;

        public BitSwapMessageImpl(boolean full) {
            this.full = full;
        }

        public int addEntry(@NonNull Cid c,
                            int priority, boolean cancel,
                            @NonNull BitswapProtos.Message.Wantlist.WantType wantType,
                            boolean sendDontHave) {
            Entry e = wantlist.get(c);
            if (e != null) {
                // Only change priority if want is of the same type
                if (e.WantType == wantType) {
                    e.Priority = priority;
                }
                // Only change from "dont cancel" to "do cancel"
                if (cancel) {
                    e.Cancel = cancel;
                }
                // Only change from "dont send" to "do send" DONT_HAVE
                if (sendDontHave) {
                    e.SendDontHave = sendDontHave;
                }
                // want-block overrides existing want-have
                if (wantType == BitswapProtos.Message.Wantlist.WantType.Block
                        && e.WantType == BitswapProtos.Message.Wantlist.WantType.Have) {
                    e.WantType = wantType;
                }
                wantlist.put(c, e); // TODO why (not really needed)
                return 0;
            }

            e = new Entry();
            e.Cid = c;
            e.Priority = priority;
            e.WantType = wantType;
            e.SendDontHave = sendDontHave;
            e.Cancel = cancel;

            wantlist.put(c, e);

            return e.Size();
        }

        @Override
        public List<Entry> Wantlist() {
            return new ArrayList<>(wantlist.values());
        }

        @Override
        public List<Block> Blocks() {
            return new ArrayList<>(blocks.values());
        }

        @Override
        public List<BlockPresence> BlockPresences() {

            List<BlockPresence> result = new ArrayList<>();
            for (Map.Entry<Cid, BitswapProtos.Message.BlockPresenceType> entry :
                    blockPresences.entrySet()) {
                BlockPresence blockPresence = new BlockPresence();
                blockPresence.Cid = entry.getKey();
                blockPresence.Type = entry.getValue();
                result.add(blockPresence);
            }
            return result;
        }

        private List<Cid> getBlockPresenceByType(BitswapProtos.Message.BlockPresenceType type) {

            List<Cid> cids = new ArrayList<>();
            for (Map.Entry<Cid, BitswapProtos.Message.BlockPresenceType> entry :
                    blockPresences.entrySet()) {
                if (entry.getValue() == type) {
                    cids.add(entry.getKey());
                }
            }
            return cids;
        }

        @Override
        public List<Cid> Haves() {
            return getBlockPresenceByType(BitswapProtos.Message.BlockPresenceType.Have);
        }

        @Override
        public List<Cid> DontHaves() {
            return getBlockPresenceByType(BitswapProtos.Message.BlockPresenceType.DontHave);
        }

        @Override
        public int PendingBytes() {
            return pendingBytes;
        }

        @Override
        public int AddEntry(@NonNull Cid key, int priority, @NonNull BitswapProtos.Message.Wantlist.WantType wantType, boolean sendDontHave) {
            return addEntry(key, priority, false, wantType, sendDontHave);
        }

        @Override
        public int Cancel(@NonNull Cid key) {
            return addEntry(key, 0, true, BitswapProtos.Message.Wantlist.WantType.Block, false);
        }

        @Override
        public void Remove(@NonNull Cid key) {
            wantlist.remove(key);
        }

        @Override
        public boolean Empty() {
            return blocks.size() == 0 && wantlist.size() == 0 && blockPresences.size() == 0;
        }

        private int BlockPresenceSize(@NonNull Cid c) {
            return BitswapProtos.Message.BlockPresence
                    .newBuilder().setCid(ByteString.copyFrom(c.Bytes()))
                    .setType(BitswapProtos.Message.BlockPresenceType.Have)
                    .build().getSerializedSize();
        }

        @Override
        public int Size() {
            int size = 0;

            for (Block b : blocks.values()) {
                size += b.RawData().length;
            }
            for (Cid c : blockPresences.keySet()) {
                size += BlockPresenceSize(c);
            }
            for (Entry e : wantlist.values()) {
                size += e.Size();
            }
            return size;
        }

        @Override
        public boolean Full() {
            return full;
        }

        @Override
        public void AddBlock(@NonNull Block block) {
            blockPresences.remove(block.Cid());
            blocks.put(block.Cid(), block);
        }

        @Override
        public void AddBlockPresence(@NonNull Cid cid, @NonNull BitswapProtos.Message.BlockPresenceType type) {
            if (blocks.containsKey(cid)) {
                return;
            }
            /* TODO check
            if _, ok := m.blocks[c]; ok {
                return
            }*/
            blockPresences.put(cid, type);
        }

        @Override
        public void AddHave(@NonNull Cid cid) {
            AddBlockPresence(cid, BitswapProtos.Message.BlockPresenceType.Have);
        }

        @Override
        public void AddDontHave(@NonNull Cid cid) {
            AddBlockPresence(cid, BitswapProtos.Message.BlockPresenceType.DontHave);
        }

        @Override
        public void SetPendingBytes(int pendingBytes) {
            this.pendingBytes = pendingBytes;
        }


        private BitswapProtos.Message ToProtoV1() {

            BitswapProtos.Message.Builder builder = BitswapProtos.Message.newBuilder();

            BitswapProtos.Message.Wantlist.Builder wantBuilder =
                    BitswapProtos.Message.Wantlist.newBuilder();


            for (Entry entry : wantlist.values()) {
                wantBuilder.addEntries(entry.ToPB());
            }
            wantBuilder.setFull(full);
            builder.setWantlist(wantBuilder.build());


            for (Block block : Blocks()) {
                builder.addPayload(BitswapProtos.Message.Block.newBuilder()
                        .setData(ByteString.copyFrom(block.RawData()))
                        .setPrefix(ByteString.copyFrom(block.Cid().Prefix().Bytes())).build());
            }


            for (Map.Entry<Cid, BitswapProtos.Message.BlockPresenceType> mapEntry :
                    blockPresences.entrySet()) {
                builder.addBlockPresences(BitswapProtos.Message.BlockPresence.newBuilder()
                        .setType(mapEntry.getValue())
                        .setCid(ByteString.copyFrom(mapEntry.getKey().Bytes())));
            }

            builder.setPendingBytes(PendingBytes());

            return builder.build();

        }

        @Override
        public byte[] ToNetV1() {
            try {
                byte[] data = ToProtoV1().toByteArray();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                Multihash.putUvarint(buf, data.length);
                buf.write(data);
                return buf.toByteArray();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }

        private BitswapProtos.Message ToProtoV0() {
            BitswapProtos.Message.Builder builder = BitswapProtos.Message.newBuilder();

            BitswapProtos.Message.Wantlist.Builder wantBuilder = BitswapProtos.Message.Wantlist.newBuilder();


            for (Entry entry : wantlist.values()) {
                wantBuilder.addEntries(entry.ToPB());
            }
            wantBuilder.setFull(full);
            builder.setWantlist(wantBuilder.build());


            for (Block block : Blocks()) {
                builder.addBlocks(ByteString.copyFrom(block.RawData()));
            }

            return builder.build();
        }

        @Override
        public void Reset(boolean full) {
            this.full = full;
            wantlist.clear();
            blocks.clear();
            blockPresences.clear();
            this.pendingBytes = 0;
        }

        @Override
        public BitSwapMessage Clone() {
            BitSwapMessageImpl msg = new BitSwapMessageImpl(full);
            msg.blockPresences.putAll(blockPresences);
            msg.blocks.putAll(blocks);
            msg.wantlist.putAll(wantlist);
            msg.pendingBytes = pendingBytes;
            return msg;
        }

    }
}
