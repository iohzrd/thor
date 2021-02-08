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
    private final BookmarkDatabase bookmarkDatabase;


    private BOOKS(final BOOKS.Builder builder) {
        bookmarkDatabase = builder.bookmarkDatabase;
    }

    @NonNull
    private static BOOKS createPages(@NonNull BookmarkDatabase bookmarkDatabase) {

        return new BOOKS.Builder()
                .bookmarkDatabase(bookmarkDatabase)
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
        bookmarkDatabase.bookmarkDao().insertBookmark(bookmark);
    }

    public void setBookmarkContent(@NonNull String uri, @NonNull String content) {
        bookmarkDatabase.bookmarkDao().setContent(uri, content);
    }

    public void setBookmarkSequence(@NonNull String uri, long sequence) {
        bookmarkDatabase.bookmarkDao().setSequence(uri, sequence);
    }

    @NonNull
    public BookmarkDatabase getBookmarkDatabase() {
        return bookmarkDatabase;
    }

    @Nullable
    public Bookmark getBookmark(@NonNull String uri) {
        return bookmarkDatabase.bookmarkDao().getBookmark(uri);
    }

    public boolean hasBookmark(@NonNull String uri) {
        return getBookmark(uri) != null;
    }

    public void removeBookmark(@NonNull Bookmark bookmark) {
        bookmarkDatabase.bookmarkDao().removeBookmark(bookmark);
    }

    public List<Bookmark> getBookmarksByQuery(@NonNull String query) {

        String searchQuery = query.trim();
        if (!searchQuery.startsWith("%")) {
            searchQuery = "%" + searchQuery;
        }
        if (!searchQuery.endsWith("%")) {
            searchQuery = searchQuery + "%";
        }
        return bookmarkDatabase.bookmarkDao().getBookmarksByQuery(searchQuery);
    }


    static class Builder {

        BookmarkDatabase bookmarkDatabase = null;

        BOOKS build() {

            return new BOOKS(this);
        }

        BOOKS.Builder bookmarkDatabase(@NonNull BookmarkDatabase bookmarkDatabase) {

            this.bookmarkDatabase = bookmarkDatabase;
            return this;
        }
    }
}
