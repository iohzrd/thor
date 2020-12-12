package threads.thor.core.page;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Bookmark.class}, version = 3, exportSchema = false)
public abstract class BookmarkDatabase extends RoomDatabase {


    public abstract BookmarkDao bookmarkDao();

}
