package threads.thor.core.pages;


import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;


@Dao
public interface PageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPage(Page page);

    @Query("SELECT * FROM Page WHERE pid = :pid")
    Page getPage(String pid);

    @Query("SELECT content FROM Page WHERE pid = :pid")
    String getPageContent(String pid);

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
}
