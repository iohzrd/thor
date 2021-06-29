package threads.lite.format;

import androidx.annotation.NonNull;

import java.util.List;

import threads.lite.cid.Cid;
import threads.lite.data.Dshelp;
import threads.lite.data.Storage;

public interface BlockStore {


    static BlockStore createBlockStore(@NonNull final Storage storage) {
        return new BlockStore() {
            @Override
            public boolean hasBlock(@NonNull Cid cid) {
                String key = Dshelp.cidToDsKey(cid).getKey();
                return storage.hasBlock(key);
            }

            @Override
            public Block getBlock(@NonNull Cid cid) {

                String key = Dshelp.cidToDsKey(cid).getKey();
                byte[] data = storage.getData(key);
                if (data == null) {
                    return null;
                }
                return BasicBlock.createBlockWithCid(cid, data);
            }

            @Override
            public void putBlock(@NonNull Block block) {
                String key = Dshelp.cidToDsKey(block.getCid()).getKey();
                storage.insertBlock(key, block.getRawData());
            }

            @Override
            public int getSize(@NonNull Cid cid) {
                String key = Dshelp.cidToDsKey(cid).getKey();
                return storage.sizeBlock(key);
            }

            public void deleteBlock(@NonNull Cid cid) {
                String key = Dshelp.cidToDsKey(cid).getKey();
                storage.deleteBlock(key);
            }

            @Override
            public void deleteBlocks(@NonNull List<Cid> cids) {
                for (Cid cid : cids) {
                    deleteBlock(cid);
                }
            }

        };
    }

    boolean hasBlock(@NonNull Cid cid);

    Block getBlock(@NonNull Cid cid);

    void deleteBlock(@NonNull Cid cid);

    void deleteBlocks(@NonNull List<Cid> cids);

    void putBlock(@NonNull Block block);

    int getSize(@NonNull Cid cid);
}


