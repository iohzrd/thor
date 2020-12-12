package threads.thor.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import threads.LogUtils;
import threads.thor.R;
import threads.thor.core.events.EVENTS;
import threads.thor.core.page.Bookmark;
import threads.thor.core.page.PAGES;


public class BookmarksViewAdapter extends RecyclerView.Adapter<BookmarksViewAdapter.ViewHolder> {
    private static final String TAG = BookmarksViewAdapter.class.getSimpleName();
    private final BookmarkListener mListener;
    private final Context mContext;
    private final List<Bookmark> bookmarks = new ArrayList<>();

    public BookmarksViewAdapter(@NonNull Context context, @NonNull BookmarkListener listener) {
        this.mContext = context;
        this.mListener = listener;
    }


    public void setBookmarks(@NonNull List<Bookmark> bookmarks) {
        this.bookmarks.clear();
        this.bookmarks.addAll(bookmarks);
        this.notifyDataSetChanged();
    }


    @Override
    public int getItemViewType(int position) {
        return R.layout.bookmarks;
    }

    @Override
    @NonNull
    public BookmarksViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                              int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false);
        return new ViewHolder(v);

    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {


        Bookmark bookmark = bookmarks.get(position);

        try {
            holder.bookmark_title.setText(bookmark.getTitle());
            holder.bookmark_uri.setText(bookmark.getUri());
            int res = R.drawable.bookmark;

            Bitmap image = bookmark.getBitmapIcon();
            if (image != null) {
                holder.bookmark_image.setImageBitmap(image);
            } else {
                holder.bookmark_image.setImageResource(res);
            }

            holder.view.setClickable(true);
            holder.view.setFocusable(false);
            holder.view.setOnClickListener((v) -> {
                try {
                    mListener.onClick(bookmark);
                } catch (Throwable e) {
                    LogUtils.error(TAG, e);
                }

            });


        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }


    }


    @Override
    public int getItemCount() {
        return bookmarks.size();
    }


    public void deleteItem(int position) {
        try {
            Bookmark bookmark = bookmarks.get(position);
            if (bookmark != null) {
                String name = bookmark.getTitle();
                PAGES pages = PAGES.getInstance(mContext);
                pages.removeBookmark(bookmark);

                EVENTS.getInstance(mContext).info(
                        mContext.getString(R.string.bookmark_removed, name));
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    public interface BookmarkListener {
        void onClick(@NonNull Bookmark bookmark);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final View view;
        final TextView bookmark_uri;
        final TextView bookmark_title;
        final ImageView bookmark_image;

        ViewHolder(View v) {
            super(v);

            view = v;
            bookmark_title = itemView.findViewById(R.id.bookmark_title);
            bookmark_uri = itemView.findViewById(R.id.bookmark_uri);
            bookmark_image = itemView.findViewById(R.id.bookmark_image);
        }

    }
}
