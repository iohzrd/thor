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
}
