package io.ipfs.blockservice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.ipfs.cid.Cid;
import io.ipfs.core.Closeable;
import io.ipfs.core.ClosedException;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;

public interface BlockService extends BlockGetter {


    static BlockService createBlockService(@NonNull final BlockStore bs, @NonNull final Interface rem) {
        return new BlockService() {

            @Override
            @Nullable
            public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {
                Block block = bs.Get(cid);
                if (block != null) {
                    return block;
                }
                return rem.getBlock(closeable, cid, root);
            }

            @Override
            public void addBlock(@NonNull Block block) {
                bs.Put(block);
            }

            @Override
            public void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids) {
                List<Cid> preload = new ArrayList<>();
                for (Cid cid : cids) {
                    if (!bs.Has(cid)) {
                        preload.add(cid);
                    }
                }
                if (!preload.isEmpty()) {
                    rem.preload(closeable, preload);
                }
            }

        };
    }


}
