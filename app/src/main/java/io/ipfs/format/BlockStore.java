package io.ipfs.format;

import androidx.annotation.NonNull;

import java.util.List;

import io.ipfs.datastore.Storage;
import io.ipfs.cid.Cid;
import io.ipfs.datastore.Dshelp;

public interface BlockStore {


    static BlockStore NewBlockstore(@NonNull final Storage storage) {
        return new BlockStore() {
            @Override
            public boolean Has(@NonNull Cid cid) {
                String key = Dshelp.CidToDsKey(cid).String();
                return storage.hasBlock(key);
            }

            @Override
            public Block Get(@NonNull Cid cid) {

                String key = Dshelp.CidToDsKey(cid).String();
                byte[] data = storage.getData(key);
                if (data == null) {
                    return null;
                }
                return BasicBlock.NewBlockWithCid(cid, data);
            }

            @Override
            public void Put(@NonNull Block block) {
                String key = Dshelp.CidToDsKey(block.Cid()).String();
                storage.insertBlock(key, block.RawData());
            }

            @Override
            public int GetSize(@NonNull Cid cid) {
                String key = Dshelp.CidToDsKey(cid).String();
                return storage.sizeBlock(key);
            }

            public void DeleteBlock(@NonNull Cid cid) {
                String key = Dshelp.CidToDsKey(cid).String();
                storage.deleteBlock(key);
            }

            @Override
            public void DeleteBlocks(@NonNull List<Cid> cids) {
                for (Cid cid : cids) {
                    DeleteBlock(cid);
                }
            }

        };
    }

    boolean Has(@NonNull Cid cid);

    Block Get(@NonNull Cid cid);

    void DeleteBlock(@NonNull Cid cid);

    void DeleteBlocks(@NonNull List<Cid> cids);

    void Put(@NonNull Block block);

    int GetSize(@NonNull Cid cid);
}


