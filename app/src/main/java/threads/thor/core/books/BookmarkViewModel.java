package threads.thor.core.books;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class BookmarkViewModel extends AndroidViewModel {
    private final BookmarkDatabase bookmarkDatabase;

    public BookmarkViewModel(@NonNull Application application) {
        super(application);
        bookmarkDatabase = BOOKS.getInstance(
                application.getApplicationContext()).getBookmarkDatabase();
    }

    public LiveData<List<Bookmark>> getBookmarks() {
        return bookmarkDatabase.bookmarkDao().getLiveDataBookmarks();
    }
}
