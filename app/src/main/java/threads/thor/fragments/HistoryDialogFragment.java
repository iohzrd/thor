package threads.thor.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Objects;

import threads.LogUtils;
import threads.thor.MainActivity;
import threads.thor.R;
import threads.thor.utils.HistoryViewAdapter;

public class HistoryDialogFragment extends BottomSheetDialogFragment implements HistoryViewAdapter.HistoryListener {
    public static final String TAG = HistoryDialogFragment.class.getSimpleName();

    private static final int CLICK_OFFSET = 500;
    private long mLastClickTime = 0;
    private Context mContext;
    private ActionListener mListener;

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
        mListener = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mListener = (ActionListener) getActivity();
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setContentView(R.layout.history_view);

        RecyclerView history = dialog.findViewById(R.id.history);
        Objects.requireNonNull(history);


        history.setLayoutManager(new LinearLayoutManager(mContext));
        HistoryViewAdapter mHistoryViewAdapter = new HistoryViewAdapter(this,
                mListener.getWebView().copyBackForwardList());
        history.setAdapter(mHistoryViewAdapter);
        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        return dialog;
    }

    @Override
    public void onClick(@NonNull String url) {
        if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        try {

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url),
                    mContext, MainActivity.class);
            startActivity(intent);

            dismiss();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }
}
