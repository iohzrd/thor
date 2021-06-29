package threads.lite.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import java.util.List;


public class BLOCKS implements Storage {
    private static BLOCKS INSTANCE = null;
    private final BlocksDatabase blocksDatabase;

    private BLOCKS(BLOCKS.Builder builder) {
        this.blocksDatabase = builder.blocksDatabase;
    }

    @NonNull
    private static BLOCKS createBlocks(@NonNull BlocksDatabase blocksDatabase) {

        return new BLOCKS.Builder()
                .blocksDatabase(blocksDatabase)
                .build();
    }

    public static BLOCKS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (BLOCKS.class) {
                if (INSTANCE == null) {
                    BlocksDatabase blocksDatabase = Room.databaseBuilder(context, BlocksDatabase.class,
                            BlocksDatabase.class.getSimpleName()).
                            allowMainThreadQueries().
                            fallbackToDestructiveMigration().build();

                    INSTANCE = BLOCKS.createBlocks(blocksDatabase);
                }
            }
        }
        return INSTANCE;
    }

    @Nullable
    @Override
    public byte[] getData(@NonNull String id) {
        Block block = getBlock(id);
        if (block != null) {
            return block.getData();
        }
        return null;
    }

    public void clear() {
        getBlocksDatabase().clearAllTables();
    }


    @NonNull
    public BlocksDatabase getBlocksDatabase() {
        return blocksDatabase;
    }


    @NonNull
    private Block createBlock(@NonNull String id, @NonNull byte[] data) {

        return Block.createBlock(id, data);
    }

    private void storeBlock(@NonNull Block block) {
        getBlocksDatabase().blockDao().insertBlock(block);
    }

    public void deleteBlock(@NonNull String id) {
        //LogUtils.error(TAG, "deleteBlock " +  id);
        getBlocksDatabase().blockDao().deleteBlock(id);
    }

    @Override
    public int sizeBlock(@NonNull String id) {
        return (int) getBlockSize(id);
    }

    public void insertBlock(@NonNull String id, @NonNull byte[] bytes) {
        //LogUtils.error(TAG, "insertBlock " +  id);
        storeBlock(createBlock(id, bytes));
    }

    public boolean hasBlock(@NonNull String id) {
        return getBlocksDatabase().blockDao().hasBlock(id);
    }

    public long getBlockSize(@NonNull String id) {
        return getBlocksDatabase().blockDao().getBlockSize(id);
    }

    @SuppressWarnings("unused")
    public List<Block> getBlocks() {
        return getBlocksDatabase().blockDao().getBlocks();
    }

    @Nullable
    public Block getBlock(@NonNull String id) {
        //LogUtils.error(TAG, "getBlock " +  id);
        return getBlocksDatabase().blockDao().getBlock(id);
    }

    static class Builder {
        BlocksDatabase blocksDatabase = null;

        BLOCKS build() {

            return new BLOCKS(this);
        }

        Builder blocksDatabase(@NonNull BlocksDatabase blocksDatabase) {

            this.blocksDatabase = blocksDatabase;
            return this;
        }
    }
}
