package threads.thor.fragments;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Objects;

import threads.lite.IPFS;
import threads.thor.R;
import threads.thor.Settings;
import threads.thor.core.DOCS;
import threads.thor.core.events.EVENTS;

public class SettingsDialogFragment extends BottomSheetDialogFragment {
    public static final String TAG = SettingsDialogFragment.class.getSimpleName();

    private Context mContext;

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

        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setContentView(R.layout.settings_view);


        SwitchMaterial enableJavascript = dialog.findViewById(R.id.enable_javascript);
        Objects.requireNonNull(enableJavascript);
        enableJavascript.setChecked(Settings.isJavascriptEnabled(mContext));
        enableJavascript.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setJavascriptEnabled(mContext, isChecked);
                    EVENTS.getInstance(mContext).javascript();
                }
        );


        SwitchMaterial enableRedirectUrl = dialog.findViewById(R.id.enable_redirect_url);
        Objects.requireNonNull(enableRedirectUrl);
        enableRedirectUrl.setChecked(Settings.isRedirectUrlEnabled(mContext));
        enableRedirectUrl.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setRedirectUrlEnabled(mContext, isChecked);
                    DOCS.getInstance(mContext).refreshRedirectOptions(mContext);
                }
        );

        SwitchMaterial enableRedirectIndex = dialog.findViewById(R.id.enable_redirect_index);
        Objects.requireNonNull(enableRedirectIndex);
        enableRedirectIndex.setChecked(Settings.isRedirectIndexEnabled(mContext));
        enableRedirectIndex.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    Settings.setRedirectIndexEnabled(mContext, isChecked);
                    DOCS.getInstance(mContext).refreshRedirectOptions(mContext);
                }
        );


        TextView concurrency_text = dialog.findViewById(R.id.concurrency_text);
        Objects.requireNonNull(concurrency_text);
        SeekBar concurrency = dialog.findViewById(R.id.concurrency);
        Objects.requireNonNull(concurrency);


        concurrency.setMin(10);
        concurrency.setMax(100);

        int concurrencyValue = IPFS.getConcurrencyValue(mContext);

        concurrency_text.setText(getString(R.string.concurrency_value,
                String.valueOf(concurrencyValue)));
        concurrency.setProgress(concurrencyValue);
        concurrency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                IPFS.setConcurrencyValue(mContext, progress);

                concurrency_text.setText(
                        getString(R.string.concurrency_value,
                                String.valueOf(progress)));

                EVENTS.getInstance(mContext).exit(
                        getString(R.string.restart_config_changed));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // ignore, not used
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                // ignore, not used
            }
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        return dialog;
    }

}
