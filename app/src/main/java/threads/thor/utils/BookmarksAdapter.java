package threads.thor.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import threads.LogUtils;
import threads.thor.R;
import threads.thor.core.books.Bookmark;

public abstract class BookmarksAdapter extends ArrayAdapter<Bookmark> {

    private static final String TAG = Bookmark.class.getSimpleName();


    public BookmarksAdapter(@NonNull Context context, @NonNull ArrayList<Bookmark> data) {
        super(context, R.layout.bookmarks, data);
    }

    public abstract void onClick(@NonNull Bookmark bookmark);

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        Bookmark bookmark = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        ViewHolder viewHolder; // view lookup cache stored in tag


        if (convertView == null) {
            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.bookmarks, parent, false);
            viewHolder.title = (TextView) convertView.findViewById(R.id.bookmark_title);
            viewHolder.uri = (TextView) convertView.findViewById(R.id.bookmark_uri);
            viewHolder.image = (ImageView) convertView.findViewById(R.id.bookmark_image);
            viewHolder.view = convertView;

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }


        String title = bookmark.getTitle();
        viewHolder.title.setText(title);
        viewHolder.uri.setText(bookmark.getUri());
        viewHolder.image.setTag(position);

        Bitmap image = bookmark.getBitmapIcon();
        if (image != null) {
            viewHolder.image.clearColorFilter();
            viewHolder.image.setImageBitmap(image);
        } else {
            viewHolder.image.setImageResource(R.drawable.bookmark);
            if (!title.isEmpty()) {
                int color = ColorGenerator.MATERIAL.getColor(title);
                viewHolder.image.setColorFilter(color);
            }
        }

        viewHolder.view.setClickable(true);
        viewHolder.view.setFocusable(false);
        viewHolder.view.setOnClickListener((v) -> {
            try {
                onClick(bookmark);
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

        });
        return convertView;
    }

    private static class ViewHolder {
        TextView title;
        TextView uri;
        ImageView image;
        View view;
    }
}