package threads.thor.core.pages;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;

import java.util.List;


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
    public List<Page> getBootstraps(int limit) {
        return pageDatabase.pageDao().getBootstraps(limit);
    }

    @NonNull
    public Page createPage(@NonNull String pid) {
        return new Page(pid);
    }


    public void storePage(@NonNull Page page) {
        pageDatabase.pageDao().insertPage(page);
    }

    @Nullable
    public Page getPage(@NonNull String pid) {
        return pageDatabase.pageDao().getPage(pid);
    }

    @NonNull
    public PageDatabase getPageDatabase() {
        return pageDatabase;
    }


    public void setPageContent(@NonNull String pid, @NonNull String content) {
        pageDatabase.pageDao().setContent(pid, content);
    }

    public void setPageSequence(@NonNull String pid, long sequence) {
        pageDatabase.pageDao().setSequence(pid, sequence);
    }

    public void setPageAddress(@NonNull String pid, @NonNull String address) {
        pageDatabase.pageDao().setAddress(pid, address);
    }


    public void incrementRating(String pid) {
        pageDatabase.pageDao().incrementRating(pid);
    }

    public void resetBootstrap(String pid) {
        pageDatabase.pageDao().resetBootstrap(pid);
    }

    public void setBootstrap(String pid) {
        pageDatabase.pageDao().setBootstrap(pid);
    }


    public void clear() {
        getPageDatabase().clearAllTables();
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
