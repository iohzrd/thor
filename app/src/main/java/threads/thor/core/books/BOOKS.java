package threads.thor.core.books;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;


public class BOOKS {

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Bookmark "
                    + " ADD COLUMN sequence INTEGER DEFAULT 0 NOT NULL");
        }
    };
    private static BOOKS INSTANCE = null;
    private final BookmarkDatabase pageDatabase;


    private BOOKS(final BOOKS.Builder builder) {
        pageDatabase = builder.pageDatabase;
    }

    @NonNull
    private static BOOKS createPages(@NonNull BookmarkDatabase threadsDatabase) {

        return new BOOKS.Builder()
                .pageDatabase(threadsDatabase)
                .build();
    }

    public static BOOKS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (BOOKS.class) {
                if (INSTANCE == null) {
                    BookmarkDatabase pageDatabase = Room.databaseBuilder(context,
                            BookmarkDatabase.class,
                            BookmarkDatabase.class.getSimpleName()).
                            addMigrations(MIGRATION_2_3).
                            allowMainThreadQueries().
                            fallbackToDestructiveMigration().
                            build();

                    INSTANCE = BOOKS.createPages(pageDatabase);
                }
            }
        }
        return INSTANCE;
    }



    @NonNull
    public Bookmark createBookmark(@NonNull String uri, @NonNull String title) {
        return new Bookmark(uri, title);
    }

    public void storeBookmark(@NonNull Bookmark bookmark) {
        pageDatabase.bookmarkDao().insertBookmark(bookmark);
    }

    public void setBookmarkContent(@NonNull String uri, @NonNull String content) {
        pageDatabase.bookmarkDao().setContent(uri, content);
    }

    public void setBookmarkSequence(@NonNull String uri, long sequence) {
        pageDatabase.bookmarkDao().setSequence(uri, sequence);
    }

    @NonNull
    public BookmarkDatabase getPageDatabase() {
        return pageDatabase;
    }

    @Nullable
    public Bookmark getBookmark(@NonNull String uri) {
        return pageDatabase.bookmarkDao().getBookmark(uri);
    }

    public boolean hasBookmark(@NonNull String uri) {
        return getBookmark(uri) != null;
    }

    public void removeBookmark(@NonNull Bookmark bookmark) {
        pageDatabase.bookmarkDao().removeBookmark(bookmark);
    }

    public List<Bookmark> getBookmarksByQuery(@NonNull String query) {

        String searchQuery = query.trim();
        if (!searchQuery.startsWith("%")) {
            searchQuery = "%" + searchQuery;
        }
        if (!searchQuery.endsWith("%")) {
            searchQuery = searchQuery + "%";
        }
        return pageDatabase.bookmarkDao().getBookmarksByQuery(searchQuery);
    }


    static class Builder {

        BookmarkDatabase pageDatabase = null;

        BOOKS build() {

            return new BOOKS(this);
        }

        BOOKS.Builder pageDatabase(@NonNull BookmarkDatabase pageDatabase) {

            this.pageDatabase = pageDatabase;
            return this;
        }
    }
}
