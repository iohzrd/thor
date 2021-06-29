package threads.lite.dag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import threads.lite.bitswap.Interface;
import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;
import threads.lite.format.Block;
import threads.lite.format.BlockStore;

public interface BlockService extends BlockGetter {


    static BlockService createBlockService(@NonNull final BlockStore bs, @NonNull final Interface rem) {
        return new BlockService() {

            @Override
            @Nullable
            public Block getBlock(@NonNull Closeable closeable, @NonNull Cid cid, boolean root) throws ClosedException {
                Block block = bs.getBlock(cid);
                if (block != null) {
                    return block;
                }
                return rem.getBlock(closeable, cid, root);
            }

            @Override
            public void addBlock(@NonNull Block block) {
                bs.putBlock(block);
            }

            @Override
            public void preload(@NonNull Closeable closeable, @NonNull List<Cid> cids) {
                List<Cid> preload = new ArrayList<>();
                for (Cid cid : cids) {
                    if (!bs.hasBlock(cid)) {
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
