package threads.thor.core.pages;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;


public class PAGES {

    private static PAGES INSTANCE = null;

    private final PageDatabase pageDatabase;


    private PAGES(final PAGES.Builder builder) {
        pageDatabase = builder.pageDatabase;
    }

    @NonNull
    private static PAGES createPages(@NonNull PageDatabase pageDatabase) {

        return new Builder()
                .pageDatabase(pageDatabase)
                .build();
    }

    public static PAGES getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (PAGES.class) {
                if (INSTANCE == null) {
                    PageDatabase pageDatabase = Room.databaseBuilder(context,
                            PageDatabase.class,
                            PageDatabase.class.getSimpleName()).
                            allowMainThreadQueries().
                            fallbackToDestructiveMigration().
                            build();

                    INSTANCE = PAGES.createPages(pageDatabase);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    public Page createPage(@NonNull String pid) {
        return new Page(pid);
    }


    public void storePage(@NonNull Page page) {
        pageDatabase.pageDao().insertPage(page);
    }

    @Nullable
    public Page getPage(@NonNull String hash) {
        return pageDatabase.pageDao().getPage(hash);
    }

    @NonNull
    public PageDatabase getPageDatabase() {
        return pageDatabase;
    }


    @Nullable
    public String getPageContent(@NonNull String hash) {
        return pageDatabase.pageDao().getPageContent(hash);
    }


    static class Builder {

        PageDatabase pageDatabase = null;

        PAGES build() {

            return new PAGES(this);
        }

        PAGES.Builder pageDatabase(@NonNull PageDatabase pageDatabase) {

            this.pageDatabase = pageDatabase;
            return this;
        }
    }
}
