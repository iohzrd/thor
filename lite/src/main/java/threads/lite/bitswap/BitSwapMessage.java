package threads.lite.bitswap;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitswap.pb.MessageOuterClass;
import threads.lite.cid.Cid;
import threads.lite.cid.Multihash;
import threads.lite.cid.Prefix;
import threads.lite.format.BasicBlock;
import threads.lite.format.Block;


public interface BitSwapMessage {


    static int BlockPresenceSize(@NonNull Cid c) {
        return MessageOuterClass.Message.BlockPresence.newBuilder()
                .setCid(ByteString.copyFrom(c.bytes()))
                .setType(MessageOuterClass.Message.BlockPresenceType.Have).build().getSerializedSize();
    }

    static BitSwapMessage New(boolean full) {
        return new BitSwapMessageImpl(full);
    }

    static BitSwapMessage newMessageFromProto(MessageOuterClass.Message pbm) {
        BitSwapMessageImpl m = new BitSwapMessageImpl(pbm.getWantlist().getFull());
        for (MessageOuterClass.Message.Wantlist.Entry e :
                pbm.getWantlist().getEntriesList()) {
            Cid cid = new Cid(e.getBlock().toByteArray());
            if (!cid.isDefined()) {
                throw new RuntimeException("errCidMissing");
            }
            m.addEntry(cid, e.getPriority(), e.getCancel(), e.getWantType(), e.getSendDontHave());
        }
        // deprecated
        for (ByteString data : pbm.getBlocksList()) {
            // CIDv0, sha256, protobuf only
            Block block = BasicBlock.createBlock(data.toByteArray());
            m.AddBlock(block);
        }
        for (MessageOuterClass.Message.Block b : pbm.getPayloadList()) {
            ByteString prefix = b.getPrefix();
            Prefix pref = Prefix.getPrefixFromBytes(prefix.toByteArray());
            Cid cid = pref.sum(b.getData().toByteArray());
            Block block = BasicBlock.createBlockWithCid(cid, b.getData().toByteArray());
            m.AddBlock(block);
        }

        for (MessageOuterClass.Message.BlockPresence bi : pbm.getBlockPresencesList()) {
            Cid cid = new Cid(bi.getCid().toByteArray());
            if (!cid.isDefined()) {
                throw new RuntimeException("errCidMissing");
            }
            m.AddBlockPresence(cid, bi.getType());
        }


        m.pendingBytes = pbm.getPendingBytes();

        return m;
    }

    static BitSwapMessage fromData(byte[] data) throws InvalidProtocolBufferException {
        MessageOuterClass.Message message = MessageOuterClass.Message.parseFrom(data);
        return newMessageFromProto(message);
    }

    MessageOuterClass.Message ToProtoV1();

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
    int AddEntry(@NonNull Cid key, int priority, @NonNull MessageOuterClass.Message.Wantlist.WantType wantType, boolean sendDontHave);

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
    void AddBlockPresence(@NonNull Cid cid, @NonNull MessageOuterClass.Message.BlockPresenceType type);

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
    class Entry extends threads.lite.bitswap.Entry {
        public boolean Cancel;
        public boolean SendDontHave;


        // Get the size of the entry on the wire
        public int Size() {
            MessageOuterClass.Message.Wantlist.Entry epb = ToPB();
            return epb.getSerializedSize();
        }

        // Get the entry in protobuf form
        public MessageOuterClass.Message.Wantlist.Entry ToPB() {

            // TODO check if Cid is correct
            return MessageOuterClass.Message.Wantlist.Entry.newBuilder().setBlock(
                    ByteString.copyFrom(Cid.bytes())
            ).setPriority(Priority).
                    setCancel(Cancel).
                    setWantType(WantType).
                    setSendDontHave(SendDontHave).build();

        }
    }


    // BitSwapMessage is the basic interface for interacting building, encoding,
    // and decoding messages sent on the BitSwap protocol.
    // BlockPresence represents a HAVE / DONT_HAVE for a given Cid
    class BlockPresence {
        public Cid Cid;
        public MessageOuterClass.Message.BlockPresenceType Type;
    }

    class BitSwapMessageImpl implements BitSwapMessage {

        private static final String TAG = BitSwapMessage.class.getSimpleName();
        final HashMap<Cid, Entry> wantlist = new HashMap<>();
        final HashMap<Cid, Block> blocks = new HashMap<>();
        final HashMap<Cid, MessageOuterClass.Message.BlockPresenceType> blockPresences = new HashMap<>();
        boolean full;
        int pendingBytes;

        public BitSwapMessageImpl(boolean full) {
            this.full = full;
        }

        public int addEntry(@NonNull Cid c,
                            int priority, boolean cancel,
                            @NonNull MessageOuterClass.Message.Wantlist.WantType wantType,
                            boolean sendDontHave) {
            Entry entry = wantlist.get(c);
            if (entry != null) {
                // Only change priority if want is of the same type
                if (entry.WantType == wantType) {
                    entry.Priority = priority;
                }
                // Only change from "dont cancel" to "do cancel"
                if (cancel) {
                    entry.Cancel = cancel;
                }
                // Only change from "dont send" to "do send" DONT_HAVE
                if (sendDontHave) {
                    entry.SendDontHave = sendDontHave;
                }
                // want-block overrides existing want-have
                if (wantType == MessageOuterClass.Message.Wantlist.WantType.Block
                        && entry.WantType == MessageOuterClass.Message.Wantlist.WantType.Have) {
                    entry.WantType = wantType;
                }
                wantlist.put(c, entry);
                return 0;
            }

            entry = new Entry();
            entry.Cid = c;
            entry.Priority = priority;
            entry.WantType = wantType;
            entry.SendDontHave = sendDontHave;
            entry.Cancel = cancel;

            wantlist.put(c, entry);

            return entry.Size();
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
            for (Map.Entry<Cid, MessageOuterClass.Message.BlockPresenceType> entry :
                    blockPresences.entrySet()) {
                BlockPresence blockPresence = new BlockPresence();
                blockPresence.Cid = entry.getKey();
                blockPresence.Type = entry.getValue();
                result.add(blockPresence);
            }
            return result;
        }

        private List<Cid> getBlockPresenceByType(MessageOuterClass.Message.BlockPresenceType type) {

            List<Cid> cids = new ArrayList<>();
            for (Map.Entry<Cid, MessageOuterClass.Message.BlockPresenceType> entry :
                    blockPresences.entrySet()) {
                if (entry.getValue() == type) {
                    cids.add(entry.getKey());
                }
            }
            return cids;
        }

        @Override
        public List<Cid> Haves() {
            return getBlockPresenceByType(MessageOuterClass.Message.BlockPresenceType.Have);
        }

        @Override
        public List<Cid> DontHaves() {
            return getBlockPresenceByType(MessageOuterClass.Message.BlockPresenceType.DontHave);
        }

        @Override
        public int PendingBytes() {
            return pendingBytes;
        }

        @Override
        public int AddEntry(@NonNull Cid key, int priority, @NonNull MessageOuterClass.Message.Wantlist.WantType wantType, boolean sendDontHave) {
            return addEntry(key, priority, false, wantType, sendDontHave);
        }

        @Override
        public int Cancel(@NonNull Cid key) {
            return addEntry(key, 0, true, MessageOuterClass.Message.Wantlist.WantType.Block, false);
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
            return MessageOuterClass.Message.BlockPresence
                    .newBuilder().setCid(ByteString.copyFrom(c.bytes()))
                    .setType(MessageOuterClass.Message.BlockPresenceType.Have)
                    .build().getSerializedSize();
        }

        @Override
        public int Size() {
            int size = 0;

            for (Block b : blocks.values()) {
                size += b.getRawData().length;
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
            blockPresences.remove(block.getCid());
            blocks.put(block.getCid(), block);
        }

        @Override
        public void AddBlockPresence(@NonNull Cid cid,
                                     @NonNull MessageOuterClass.Message.BlockPresenceType type) {
            if (blocks.containsKey(cid)) {
                return;
            }
            blockPresences.put(cid, type);
        }

        @Override
        public void AddHave(@NonNull Cid cid) {
            AddBlockPresence(cid, MessageOuterClass.Message.BlockPresenceType.Have);
        }

        @Override
        public void AddDontHave(@NonNull Cid cid) {
            AddBlockPresence(cid, MessageOuterClass.Message.BlockPresenceType.DontHave);
        }

        @Override
        public void SetPendingBytes(int pendingBytes) {
            this.pendingBytes = pendingBytes;
        }


        public MessageOuterClass.Message ToProtoV1() {

            MessageOuterClass.Message.Builder builder = MessageOuterClass.Message.newBuilder();

            MessageOuterClass.Message.Wantlist.Builder wantBuilder =
                    MessageOuterClass.Message.Wantlist.newBuilder();


            for (Entry entry : wantlist.values()) {
                wantBuilder.addEntries(entry.ToPB());
            }
            wantBuilder.setFull(full);
            builder.setWantlist(wantBuilder.build());


            for (Block block : Blocks()) {
                builder.addPayload(MessageOuterClass.Message.Block.newBuilder()
                        .setData(ByteString.copyFrom(block.getRawData()))
                        .setPrefix(ByteString.copyFrom(block.getCid().getPrefix().bytes())).build());
            }


            for (Map.Entry<Cid, MessageOuterClass.Message.BlockPresenceType> mapEntry :
                    blockPresences.entrySet()) {
                builder.addBlockPresences(MessageOuterClass.Message.BlockPresence.newBuilder()
                        .setType(mapEntry.getValue())
                        .setCid(ByteString.copyFrom(mapEntry.getKey().bytes())));
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

        private MessageOuterClass.Message ToProtoV0() {
            MessageOuterClass.Message.Builder builder = MessageOuterClass.Message.newBuilder();

            MessageOuterClass.Message.Wantlist.Builder wantBuilder = MessageOuterClass.Message.Wantlist.newBuilder();


            for (Entry entry : wantlist.values()) {
                wantBuilder.addEntries(entry.ToPB());
            }
            wantBuilder.setFull(full);
            builder.setWantlist(wantBuilder.build());


            for (Block block : Blocks()) {
                builder.addBlocks(ByteString.copyFrom(block.getRawData()));
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
