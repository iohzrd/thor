package threads.thor.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import threads.thor.MainActivity;

public class CustomWebChromeClient extends WebChromeClient {
    private static final int FULL_SCREEN_SETTING = View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE;

    private final Activity mActivity;

    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private int mOriginalOrientation;
    private int mOriginalSystemUiVisibility;

    public CustomWebChromeClient(@NonNull Activity activity) {
        this.mActivity = activity;
    }

    @Override
    public void onHideCustomView() {
        ((FrameLayout) mActivity.getWindow().getDecorView()).removeView(this.mCustomView);
        this.mCustomView = null;
        mActivity.getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
        mActivity.setRequestedOrientation(this.mOriginalOrientation);
        this.mCustomViewCallback.onCustomViewHidden();
        this.mCustomViewCallback = null;
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, android.os.Message resultMsg) {
        WebView.HitTestResult result = view.getHitTestResult();
        String data = result.getExtra();
        Context context = view.getContext();
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(data), context, MainActivity.class);
        context.startActivity(browserIntent);
        return false;
    }

    @Override
    public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback paramCustomViewCallback) {
        this.mCustomView = paramView;
        this.mOriginalSystemUiVisibility = mActivity.getWindow().getDecorView().getSystemUiVisibility();
        this.mOriginalOrientation = mActivity.getRequestedOrientation();
        this.mCustomViewCallback = paramCustomViewCallback;
        ((FrameLayout) mActivity.getWindow()
                .getDecorView())
                .addView(this.mCustomView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mActivity.getWindow().getDecorView().setSystemUiVisibility(FULL_SCREEN_SETTING);
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        this.mCustomView.setOnSystemUiVisibilityChangeListener(visibility -> updateControls());

    }

    @Override
    public Bitmap getDefaultVideoPoster() {
        return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
    }

    void updateControls() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)
                this.mCustomView.getLayoutParams();
        params.bottomMargin = 0;
        params.topMargin = 0;
        params.leftMargin = 0;
        params.rightMargin = 0;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        this.mCustomView.setLayoutParams(params);
        mActivity.getWindow().getDecorView().setSystemUiVisibility(FULL_SCREEN_SETTING);

    }


}
