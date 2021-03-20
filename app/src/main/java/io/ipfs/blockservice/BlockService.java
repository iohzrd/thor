package io.ipfs.blockservice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.Closeable;
import io.ipfs.cid.Cid;
import io.ipfs.exchange.Fetcher;
import io.ipfs.exchange.Interface;
import io.ipfs.format.Block;
import io.ipfs.format.BlockStore;

public interface BlockService extends BlockGetter {


    static BlockService New(@NonNull final BlockStore bs, @NonNull final Interface rem) {
        return new BlockService() {

            @Override
            @Nullable
            public Block GetBlock(@NonNull Closeable closeable, @NonNull Cid cid) {
                return getBlock(closeable, cid, bs, rem);
            }

            @Override
            public void AddBlock(@NonNull Block block) {
                bs.Put(block);
            }


            @Nullable
            private Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid,
                                   @NonNull BlockStore bs, @NonNull Fetcher fetcher) {
                Block block = bs.Get(cid);
                if (block != null) {
                    return block;
                }
                return fetcher.getBlock(closeable, cid);
            }
        };
    }


}
