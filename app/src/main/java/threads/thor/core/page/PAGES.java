package threads.thor.core.page;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;


public class PAGES {

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Bookmark "
                    + " ADD COLUMN sequence INTEGER DEFAULT 0 NOT NULL");
        }
    };
    private static PAGES INSTANCE = null;
    private final BookmarkDatabase pageDatabase;
    private final ResolverDatabase resolverDatabase;

    private PAGES(final PAGES.Builder builder) {
        pageDatabase = builder.pageDatabase;
        resolverDatabase = builder.resolverDatabase;
    }

    @NonNull
    private static PAGES createPages(@NonNull BookmarkDatabase threadsDatabase,
                                     @NonNull ResolverDatabase resolverDatabase) {

        return new PAGES.Builder()
                .pageDatabase(threadsDatabase)
                .resolverDatabase(resolverDatabase)
                .build();
    }

    public static PAGES getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (PAGES.class) {
                if (INSTANCE == null) {
                    BookmarkDatabase pageDatabase = Room.databaseBuilder(context,
                            BookmarkDatabase.class,
                            BookmarkDatabase.class.getSimpleName()).
                            addMigrations(MIGRATION_2_3).
                            allowMainThreadQueries().
                            fallbackToDestructiveMigration().
                            build();

                    ResolverDatabase resolverDatabase = Room.inMemoryDatabaseBuilder(context,
                            ResolverDatabase.class).allowMainThreadQueries().
                            fallbackToDestructiveMigration().build();
                    INSTANCE = PAGES.createPages(pageDatabase, resolverDatabase);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    private Resolver createResolver(@NonNull String name, @NonNull String content) {
        return new Resolver(name, content);
    }

    private void storeResolver(@NonNull Resolver resolver) {
        resolverDatabase.resolverDao().insertResolver(resolver);
    }


    @Nullable
    public Resolver getResolver(@NonNull String name) {
        return resolverDatabase.resolverDao().getResolver(name);
    }

    public void removeResolver(@NonNull String name) {
        resolverDatabase.resolverDao().removeResolver(name);
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

    public void storeResolver(@NonNull String name, @NonNull String content) {
        storeResolver(createResolver(name, content));
    }

    @Nullable
    public String getBookmarkContent(@NonNull String uri) {
        return pageDatabase.bookmarkDao().getContent(uri);
    }


    static class Builder {

        BookmarkDatabase pageDatabase = null;
        ResolverDatabase resolverDatabase = null;

        PAGES build() {

            return new PAGES(this);
        }

        PAGES.Builder pageDatabase(@NonNull BookmarkDatabase pageDatabase) {

            this.pageDatabase = pageDatabase;
            return this;
        }

        public Builder resolverDatabase(ResolverDatabase resolverDatabase) {
            this.resolverDatabase = resolverDatabase;
            return this;
        }
    }
}
