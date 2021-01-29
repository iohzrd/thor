package threads.thor.core.blocks;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BlockDao {


    @Query("SELECT * FROM Block")
    List<Block> getBlocks();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBlock(Block block);

    @Query("DELETE FROM Block WHERE id = :id")
    void deleteBlock(String id);

    @Query("DELETE FROM Block WHERE id IN(:ids)")
    void deleteBlocks(String... ids);

    @Query("SELECT 1 FROM Block WHERE id = :id")
    boolean hasBlock(String id);

    @Query("SELECT * FROM Block WHERE id = :id")
    Block getBlock(String id);

    @Query("SELECT size FROM Block WHERE id = :id")
    long getBlockSize(String id);
}
