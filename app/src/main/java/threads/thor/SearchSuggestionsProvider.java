package threads.thor;

import android.app.SearchManager;
import android.content.Context;
import android.content.SearchRecentSuggestionsProvider;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Comparator;
import java.util.List;

import threads.LogUtils;
import threads.thor.core.books.BOOKS;
import threads.thor.core.books.Bookmark;

public class SearchSuggestionsProvider
        extends SearchRecentSuggestionsProvider {
    public static final String AUTHORITY =
            SearchSuggestionsProvider.class.getName();
    public static final int MODE = DATABASE_MODE_QUERIES;
    private static final String TAG = SearchSuggestionsProvider.class.getSimpleName();


    public SearchSuggestionsProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }


    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        String search = "";

        if (selectionArgs.length == 1) {
            search = selectionArgs[0];
        }

        Context context = getContext();
        if (context != null) {

            BOOKS BOOKS = threads.thor.core.books.BOOKS.getInstance(context);

            List<Bookmark> searches = BOOKS.getBookmarksByQuery(search);

            searches.sort(Comparator.comparing(Bookmark::getTimestamp).reversed());


            return createCursorFromResult(context, searches);
        }
        return null;
    }


    private Cursor createCursorFromResult(@NonNull Context context, @NonNull List<Bookmark> searchables) {
        String[] menuCols = new String[]{BaseColumns._ID, SearchManager.SUGGEST_COLUMN_ICON_1,
                SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2_URL,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA};

        MatrixCursor cursor = new MatrixCursor(menuCols);
        int counter = 0;


        for (Bookmark searchable : searchables) {
            String data = searchable.getUri();

            Uri uri = getIconUri(context, searchable);
            cursor.addRow(new Object[]{counter++, uri, searchable.getTitle(), searchable.getUri(), data});
        }


        return cursor;
    }

    private Uri getIconUri(@NonNull Context context, @NonNull Bookmark searchable) {


        try {
            threads.thor.FileProvider fileProvider = threads.thor.FileProvider.getInstance(context);

            byte[] bytes = searchable.getIcon();


            if (bytes.length > 0) {

                int hashCode = searchable.getUri().hashCode();
                LogUtils.error(TAG, searchable.getUri());

                File newFile = new File(fileProvider.getImageDir(), "img" + hashCode + ".png");

                if (!newFile.exists()) {
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        fos.write(bytes);
                    }
                }
                return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, newFile);

            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }

}

