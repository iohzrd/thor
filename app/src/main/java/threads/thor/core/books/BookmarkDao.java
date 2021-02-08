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

    @Query("UPDATE Bookmark SET content =:content WHERE uri = :uri")
    void setContent(String uri, String content);


    @Query("UPDATE Bookmark SET sequence = :sequence WHERE uri = :uri")
    void setSequence(String uri, long sequence);
}
