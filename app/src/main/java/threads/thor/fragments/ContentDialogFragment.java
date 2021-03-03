package threads.thor.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;

import java.util.Objects;

import threads.LogUtils;
import threads.thor.R;
import threads.thor.core.Content;

public class ContentDialogFragment extends DialogFragment {

    public static final String TAG = ContentDialogFragment.class.getSimpleName();

    private Context mContext;


    public static ContentDialogFragment newInstance(@NonNull Uri uri,
                                                    @NonNull String message,
                                                    @NonNull String url) {


        Bundle bundle = new Bundle();
        bundle.putString(Content.URI, uri.toString());
        bundle.putString(Content.TEXT, message);
        bundle.putString(Content.URL, url);
        ContentDialogFragment fragment = new ContentDialogFragment();
        fragment.setArguments(bundle);
        return fragment;


    }


    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.content_info, null);

        ImageView imageView = view.findViewById(R.id.dialog_server_info);
        Bundle bundle = getArguments();
        Objects.requireNonNull(bundle);
        String title = getString(R.string.information);
        String message = bundle.getString(Content.TEXT, "");
        Uri uri = Uri.parse(bundle.getString(Content.URI));
        Objects.requireNonNull(uri);
        String url = bundle.getString(Content.URL, "");


        TextView page = view.findViewById(R.id.page);

        if (url.isEmpty()) {
            page.setVisibility(View.GONE);
        } else {
            page.setText(url);
        }


        try {
            Glide.with(mContext).load(uri).into(imageView);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        builder.setTitle(title)
                .setMessage(message)
                .setView(view)
                .create();

        Dialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().gravity = Gravity.TOP | Gravity.CENTER;
        }

        return dialog;
    }
}
