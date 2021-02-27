package threads.thor.core.books;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;


@Dao
public interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBookmark(Bookmark bookmark);

    @Query("SELECT * FROM Bookmark WHERE uri = :uri")
    Bookmark getBookmark(String uri);

    @Query("SELECT * FROM Bookmark")
    LiveData<List<Bookmark>> getLiveDataBookmarks();

    @Query("SELECT * FROM Bookmark WHERE uri LIKE :query OR title LIKE :query")
    List<Bookmark> getBookmarksByQuery(String query);

    @Delete
    void removeBookmark(Bookmark bookmark);

    @Query("SELECT content FROM Bookmark WHERE uri =:uri")
    String getDnsLink(String uri);

    @Query("UPDATE Bookmark SET content = :link WHERE uri = :uri")
    void setDnsLink(String uri, String link);

    @Query("UPDATE Bookmark SET title = :title WHERE uri = :uri")
    void setTitle(String uri, String title);
}
