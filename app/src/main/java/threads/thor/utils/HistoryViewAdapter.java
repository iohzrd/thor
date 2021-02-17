package threads.thor.utils;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import threads.LogUtils;
import threads.thor.R;


public class HistoryViewAdapter extends RecyclerView.Adapter<HistoryViewAdapter.ViewHolder> {
    private static final String TAG = HistoryViewAdapter.class.getSimpleName();
    private final HistoryListener mListener;
    private final WebBackForwardList mWebBackForwardList;

    public HistoryViewAdapter(@NonNull HistoryListener listener, @NonNull WebBackForwardList list) {
        this.mListener = listener;
        mWebBackForwardList = list;
    }


    @Override
    public int getItemViewType(int position) {
        return R.layout.history;
    }

    @Override
    @NonNull
    public HistoryViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                            int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false);
        return new ViewHolder(v);

    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        int pos = getItemCount() - (position + 1);

        WebHistoryItem history = mWebBackForwardList.getItemAtIndex(pos);

        try {
            String title = history.getTitle();
            holder.title.setText(title);
            holder.uri.setText(history.getUrl());

            Bitmap image = history.getFavicon();
            if (image != null) {
                holder.image.clearColorFilter();
                holder.image.setImageBitmap(image);
            } else {
                holder.image.setImageResource(R.drawable.bookmark);
                if (title != null && !title.isEmpty()) {
                    int color = ColorGenerator.MATERIAL.getColor(title);
                    holder.image.setColorFilter(color);
                }
            }

            holder.view.setClickable(true);
            holder.view.setFocusable(false);
            holder.view.setOnClickListener((v) -> {
                try {
                    mListener.onClick(history.getUrl());
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
        return mWebBackForwardList.getSize();
    }


    public interface HistoryListener {
        void onClick(@NonNull String url);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final View view;
        final TextView uri;
        final TextView title;
        final ImageView image;

        ViewHolder(View v) {
            super(v);

            view = v;
            title = itemView.findViewById(R.id.history_title);
            uri = itemView.findViewById(R.id.history_uri);
            image = itemView.findViewById(R.id.history_image);
        }

    }
}
