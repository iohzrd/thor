package threads.thor.core.pages;


import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;


@Dao
public interface PageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPage(Page page);

    @Query("SELECT * FROM Page WHERE pid = :pid")
    Page getPage(String pid);

    @Query("UPDATE Page SET content =:content WHERE pid = :pid")
    void setContent(String pid, String content);

    @Query("UPDATE Page SET sequence = :sequence WHERE pid = :pid")
    void setSequence(String pid, long sequence);

    @Query("UPDATE Page SET address = :address WHERE pid = :pid")
    void setAddress(String pid, String address);

    @Query("UPDATE Page SET bootstrap = 0, rating = 0 WHERE pid = :pid")
    void resetBootstrap(String pid);

    @Query("UPDATE Page SET bootstrap = 1 WHERE pid = :pid")
    void setBootstrap(String pid);

    @Query("UPDATE Page SET rating = rating + 1  WHERE pid = :pid")
    void incrementRating(String pid);

    @Query("SELECT * FROM Page WHERE bootstrap = 1 ORDER BY rating DESC LIMIT :limit")
    List<Page> getBootstraps(int limit);
}
