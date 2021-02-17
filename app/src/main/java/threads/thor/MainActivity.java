package threads.thor;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.LogUtils;
import threads.thor.core.Content;
import threads.thor.core.DOCS;
import threads.thor.core.books.BOOKS;
import threads.thor.core.books.Bookmark;
import threads.thor.core.events.EVENTS;
import threads.thor.core.events.EventViewModel;
import threads.thor.fragments.ActionListener;
import threads.thor.fragments.BookmarksDialogFragment;
import threads.thor.fragments.HistoryDialogFragment;
import threads.thor.ipfs.Closeable;
import threads.thor.ipfs.IPFS;
import threads.thor.magnet.magnet.MagnetUri;
import threads.thor.magnet.magnet.MagnetUriParser;
import threads.thor.services.ThorService;
import threads.thor.utils.AdBlocker;
import threads.thor.utils.CustomWebChromeClient;
import threads.thor.utils.FileDocumentsProvider;
import threads.thor.utils.MimeType;
import threads.thor.work.ClearBrowserDataWorker;
import threads.thor.work.DownloadContentWorker;
import threads.thor.work.DownloadFileWorker;
import threads.thor.work.DownloadMagnetWorker;
import threads.thor.work.PageResolveWorker;


public class MainActivity extends AppCompatActivity implements
        ActionListener {

    public static final String SHOW_DOWNLOADS = "SHOW_DOWNLOADS";
    public static final AtomicBoolean PROXY = new AtomicBoolean(false);
    private static final String DOWNLOADS = "content://com.android.externalstorage.documents/document/primary:Download";
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final long CLICK_OFFSET = 500;

    private final ActivityResultLauncher<Intent> mFolderRequestForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();

                    try {
                        Objects.requireNonNull(data);
                        Uri uri = data.getData();
                        Objects.requireNonNull(uri);


                        String mimeType = getContentResolver().getType(uri);


                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, mimeType);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                        startActivity(intent);

                    } catch (Throwable e) {
                        EVENTS.getInstance(getApplicationContext()).warning(
                                getString(R.string.no_activity_found_to_handle_uri));
                    }
                }
            });
    private final ActivityResultLauncher<Intent> mFileForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    try {
                        Objects.requireNonNull(data);

                        Uri uri = data.getData();
                        Objects.requireNonNull(uri);
                        if (!FileDocumentsProvider.hasWritePermission(getApplicationContext(), uri)) {
                            EVENTS.getInstance(getApplicationContext()).error(
                                    getString(R.string.file_has_no_write_permission));
                            return;
                        }
                        ThorService.FileInfo fileInfo = ThorService.getFileInfo(getApplicationContext());
                        Objects.requireNonNull(fileInfo);
                        DownloadFileWorker.download(getApplicationContext(), uri, fileInfo.getUri(),
                                fileInfo.getFilename(), fileInfo.getMimeType(), fileInfo.getSize());


                    } catch (Throwable e) {
                        LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
                    }
                }
            });


    private final ActivityResultLauncher<Intent> mContentForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    try {
                        Objects.requireNonNull(data);
                        Uri uri = data.getData();
                        Objects.requireNonNull(uri);
                        if (!FileDocumentsProvider.hasWritePermission(getApplicationContext(), uri)) {
                            EVENTS.getInstance(getApplicationContext()).error(
                                    getString(R.string.file_has_no_write_permission));
                            return;
                        }
                        Uri contentUri = ThorService.getContentUri(getApplicationContext());
                        Objects.requireNonNull(contentUri);
                        DownloadContentWorker.download(getApplicationContext(), uri, contentUri);


                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            });


    private final ActivityResultLauncher<Intent> mMagnetForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    try {
                        Objects.requireNonNull(data);

                        Uri uri = data.getData();
                        Objects.requireNonNull(uri);
                        if (!FileDocumentsProvider.hasWritePermission(getApplicationContext(), uri)) {
                            EVENTS.getInstance(getApplicationContext()).error(
                                    getString(R.string.file_has_no_write_permission));
                            return;
                        }
                        Uri magnet = Uri.parse(ThorService.getMagnet(getApplicationContext()));
                        Objects.requireNonNull(magnet);
                        DownloadMagnetWorker.download(getApplicationContext(), magnet, uri);


                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
            });

    private WebView mWebView;
    private long mLastClickTime = 0;
    private TextView mBrowserText;
    private ActionMode mActionMode;
    private CustomWebChromeClient mCustomWebChromeClient;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ProgressBar mProgressBar;
    private ImageButton mActionBookmark;
    private DOCS docs;
    private AppBarLayout mAppBar;

    private void magnetDownloader(@NonNull Uri uri, @NonNull String name) {


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.download_title);
        builder.setMessage(name);

        builder.setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {

            ThorService.setMagnet(getApplicationContext(), uri.toString());

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(DOWNLOADS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mMagnetForResult.launch(intent);

            mProgressBar.setVisibility(View.GONE);
        });
        builder.setNeutralButton(getString(android.R.string.cancel),
                (dialog, which) -> {
                    mProgressBar.setVisibility(View.GONE);
                    dialog.cancel();
                });
        builder.show();


    }

    private void contentDownloader(@NonNull Uri uri) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.download_title);
        builder.setMessage(docs.getFileName(uri));

        builder.setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {

            ThorService.setContentUri(getApplicationContext(), uri);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(DOWNLOADS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mContentForResult.launch(intent);
            mProgressBar.setVisibility(View.GONE);
        });
        builder.setNeutralButton(getString(android.R.string.cancel),
                (dialog, which) -> {
                    mProgressBar.setVisibility(View.GONE);
                    dialog.cancel();
                });
        builder.show();


    }

    private void fileDownloader(@NonNull Uri uri, @NonNull String filename,
                                @NonNull String mimeType, long size) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.download_title);
        builder.setMessage(filename);

        builder.setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {

            ThorService.setFileInfo(getApplicationContext(), uri, filename, mimeType, size);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(DOWNLOADS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mFileForResult.launch(intent);
            mProgressBar.setVisibility(View.GONE);

        });
        builder.setNeutralButton(getString(android.R.string.cancel),
                (dialog, which) -> {
                    mProgressBar.setVisibility(View.GONE);
                    dialog.cancel();
                });
        builder.show();

    }

    public void reload() {

        try {
            mProgressBar.setVisibility(View.GONE);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        try {
            docs.cleanupResolver(Uri.parse(mWebView.getUrl()));
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        try {
            mWebView.reload();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    private ActionMode.Callback createFindActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_find_action_mode, menu);


                MenuItem action_mode_find = menu.findItem(R.id.action_mode_find);
                EditText mFindText = (EditText) action_mode_find.getActionView();

                mFindText.setMaxWidth(Integer.MAX_VALUE);
                mFindText.setSingleLine();
                mFindText.setTextSize(14);
                mFindText.setHint(R.string.find_page);
                mFindText.setFocusable(true);
                mFindText.requestFocus();

                mFindText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        mWebView.findAllAsync(mFindText.getText().toString());
                    }
                });


                mode.setTitle("0/0");

                mWebView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
                    try {
                        String result = "" + activeMatchOrdinal + "/" + numberOfMatches;
                        mode.setTitle(result);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                });

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return true;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                int itemId = item.getItemId();

                if (itemId == R.id.action_mode_previous) {
                    try {
                        mWebView.findNext(false);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                    return true;
                } else if (itemId == R.id.action_mode_next) {
                    try {
                        mWebView.findNext(true);
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                try {
                    mWebView.clearMatches();
                    mWebView.setFindListener(null);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            }
        };

    }

    public boolean onBackPressedCheck() {

        if (mWebView.canGoBack()) {
            goBack();
            return true;
        }

        return false;
    }

    private void checkBookmarkState() {

        try {
            String url = mWebView.getUrl();
            if (url != null && !url.isEmpty()) {
                BOOKS books = BOOKS.getInstance(getApplicationContext());
                Uri uri = docs.getOriginalUri(Uri.parse(url));
                if (books.hasBookmark(uri.toString())) {
                    Drawable drawable = AppCompatResources.getDrawable(
                            getApplicationContext(), R.drawable.star);
                    mActionBookmark.setImageDrawable(drawable);
                } else {
                    Drawable drawable = AppCompatResources.getDrawable(
                            getApplicationContext(), R.drawable.star_outline);
                    mActionBookmark.setImageDrawable(drawable);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

    }

    private String prettyUri(@NonNull Uri uri, @NonNull String replace) {
        return uri.toString().replaceFirst(replace, "");
    }

    private void invalidateMenu(@Nullable Uri uri) {
        try {
            if (uri != null) {

                if (Objects.equals(uri.getScheme(), Content.HTTPS)) {
                    mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.lock, 0, 0, 0
                    );
                    mBrowserText.setText(prettyUri(uri, "https://"));
                } else if (Objects.equals(uri.getScheme(), Content.HTTP)) {
                    mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.lock_open, 0, 0, 0
                    );
                    mBrowserText.setText(prettyUri(uri, "http://"));
                } else {
                    mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.lock, 0, 0, 0
                    );
                    mBrowserText.setText(uri.toString());
                }

            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        } finally {
            checkBookmarkState();
        }
    }

    private void goBack() {
        try {
            mWebView.stopLoading();
            docs.releaseThreads();
            mWebView.goBack();
            mAppBar.setExpanded(true, true);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private void goForward() {
        try {
            mWebView.stopLoading();
            docs.releaseThreads();
            mWebView.goForward();
            mAppBar.setExpanded(true, true);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private int dpToPx(int dp) {
        float density = getApplicationContext().getResources()
                .getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }


    @SuppressLint({"ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        docs = DOCS.getInstance(getApplicationContext());


        CoordinatorLayout mDrawerLayout = findViewById(R.id.drawer_layout);
        mAppBar = findViewById(R.id.appbar);
        mProgressBar = findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.GONE);
        mWebView = findViewById(R.id.web_view);
        mSwipeRefreshLayout = findViewById(R.id.swipe_container);

        mAppBar.addOnOffsetChangedListener(new AppBarStateChangedListener() {
            @Override
            public void onStateChanged(AppBarLayout appBarLayout, State state) {
                if (state == State.EXPANDED) {
                    mSwipeRefreshLayout.setEnabled(true);
                } else if (state == State.COLLAPSED) {
                    mSwipeRefreshLayout.setEnabled(false);
                }

            }
        });

        Settings.setWebSettings(mWebView);


        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(mWebView.getSettings(), WebSettingsCompat.FORCE_DARK_AUTO);
        }

        if (Settings.THEME_ACTIVE && !isDarkTheme()) {
            mWebView.addJavascriptInterface(new JsInterface(getApplicationContext()), "CC_FUND");
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, false);

        ImageButton mActionIncognito = findViewById(R.id.action_incognito);
        mActionIncognito.setOnClickListener(v -> {
            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                PROXY.set(!PROXY.get());

                if (!PROXY.get()) {
                    Settings.setIncognitoMode(mWebView, false);

                    mActionIncognito.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                            android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);

                } else {
                    Settings.setIncognitoMode(mWebView, true);

                    mActionIncognito.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                            android.R.color.holo_green_light), android.graphics.PorterDuff.Mode.SRC_IN);

                    EVENTS.getInstance(getApplicationContext()).error(
                            getString(R.string.tor_mode));
                }

                invalidateMenu(null);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        if (!PROXY.get()) {
            Settings.setIncognitoMode(mWebView, false);

            mActionIncognito.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            Settings.setIncognitoMode(mWebView, true);

            mActionIncognito.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.holo_green_light), android.graphics.PorterDuff.Mode.SRC_IN);
        }


        ImageView mActionOverflow = findViewById(R.id.action_overflow);

        mActionOverflow.setOnClickListener(v -> {

            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);


            View menuOverflow = inflater.inflate(
                    R.layout.menu_overflow, mDrawerLayout, false);


            Dialog dialog = new Dialog(MainActivity.this);

            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(menuOverflow);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.TOP | Gravity.END);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (!isDarkTheme()) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.popup);
            }
            dialog.show();


            /*
            PopupWindow dialog = new PopupWindow(
                    MainActivity.this, null, android.R.attr.popupMenuStyle);
            dialog.setContentView(menuOverflow);
            dialog.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.setOutsideTouchable(true);
            dialog.setFocusable(true);
            dialog.showAsDropDown(mActionOverflow, 0, -dpToPx(android.R.attr.actionBarSize), Gravity.TOP);*/


            ImageButton actionNextPage = menuOverflow.findViewById(R.id.action_next_page);
            if (!mWebView.canGoForward()) {
                actionNextPage.setEnabled(false);

                actionNextPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        android.R.color.darker_gray), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionNextPage.setEnabled(true);

                actionNextPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        android.R.color.black), android.graphics.PorterDuff.Mode.SRC_IN);
            }
            actionNextPage.setOnClickListener(v1 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    goForward();
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            ImageButton actionFindPage = menuOverflow.findViewById(R.id.action_find_page);

            actionFindPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.black), android.graphics.PorterDuff.Mode.SRC_IN);
            actionFindPage.setOnClickListener(v12 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    startSupportActionMode(
                            createFindActionModeCallback());
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            ImageButton actionDownload = menuOverflow.findViewById(R.id.action_download);

            if (downloadActive()) {
                actionDownload.setEnabled(true);

                actionDownload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        android.R.color.black), android.graphics.PorterDuff.Mode.SRC_IN);
            } else {
                actionDownload.setEnabled(false);

                actionDownload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                        android.R.color.darker_gray), android.graphics.PorterDuff.Mode.SRC_IN);
            }

            actionDownload.setOnClickListener(v13 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    download();
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            ImageButton actionShare = menuOverflow.findViewById(R.id.action_share);

            actionShare.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.black), android.graphics.PorterDuff.Mode.SRC_IN);
            actionShare.setOnClickListener(v14 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    String url = mWebView.getUrl();
                    Uri uri = docs.getOriginalUri(Uri.parse(url));

                    ComponentName[] names = {new ComponentName(getApplicationContext(), MainActivity.class)};

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_link));
                    intent.putExtra(Intent.EXTRA_TEXT, uri.toString());
                    intent.setType(MimeType.PLAIN_MIME_TYPE);
                    intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


                    Intent chooser = Intent.createChooser(intent, getText(R.string.share));
                    chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
                    startActivity(chooser);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

            ImageButton actionReload = menuOverflow.findViewById(R.id.action_reload);
            actionReload.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.black), android.graphics.PorterDuff.Mode.SRC_IN);
            actionReload.setOnClickListener(v15 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    reload();
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionHistory = menuOverflow.findViewById(R.id.action_history);
            actionHistory.setOnClickListener(v16 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    HistoryDialogFragment dialogFragment = new HistoryDialogFragment();
                    dialogFragment.show(getSupportFragmentManager(), HistoryDialogFragment.TAG);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionDownloads = menuOverflow.findViewById(R.id.action_downloads);
            actionDownloads.setOnClickListener(v17 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    Uri root = Uri.parse(DOWNLOADS);
                    showDownloads(root);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionCleanup = menuOverflow.findViewById(R.id.action_cleanup);
            actionCleanup.setOnClickListener(v18 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();


                    mWebView.clearHistory();
                    mWebView.clearCache(true);
                    mWebView.clearFormData();


                    // Clear data and cookies
                    ClearBrowserDataWorker.clearCache(getApplicationContext());

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });


            TextView actionDocumentation = menuOverflow.findViewById(R.id.action_documentation);
            actionDocumentation.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    String uri = "https://gitlab.com/remmer.wilts/thor";

                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri),
                            getApplicationContext(), MainActivity.class);
                    startActivity(intent);
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                } finally {
                    dialog.dismiss();
                }

            });

        });


        mActionBookmark = findViewById(R.id.action_bookmark);
        mActionBookmark.setOnClickListener(v -> {

            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                String url = mWebView.getUrl();
                Uri uri = docs.getOriginalUri(Uri.parse(url));

                BOOKS books = BOOKS.getInstance(getApplicationContext());

                Bookmark bookmark = books.getBookmark(uri.toString());
                if (bookmark != null) {
                    String name = bookmark.getTitle();
                    books.removeBookmark(bookmark);
                    EVENTS.getInstance(getApplicationContext()).warning(
                            getString(R.string.bookmark_removed, name));
                } else {
                    Bitmap bitmap = mCustomWebChromeClient.getFavicon(url);

                    String title = mCustomWebChromeClient.getTitle(url);

                    if (title == null) {
                        title = "" + mWebView.getTitle();
                    }

                    bookmark = books.createBookmark(uri.toString(), title);
                    if (bitmap != null) {
                        bookmark.setBitmapIcon(bitmap);
                    }

                    books.storeBookmark(bookmark);


                    EVENTS.getInstance(getApplicationContext()).warning(
                            getString(R.string.bookmark_added, title));
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                checkBookmarkState();
            }
        });

        ImageView mActionBookmarks = findViewById(R.id.action_bookmarks);
        mActionBookmarks.setOnClickListener(v -> {
            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                BookmarksDialogFragment dialogFragment = new BookmarksDialogFragment();
                dialogFragment.show(getSupportFragmentManager(), BookmarksDialogFragment.TAG);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            try {
                mSwipeRefreshLayout.setRefreshing(true);
                reload();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_dark);

        mBrowserText = findViewById(R.id.action_browser);
        mBrowserText.setClickable(true);


        if (isDarkTheme()) {
            mBrowserText.setBackgroundResource(R.drawable.round_dark);
        } else {
            mBrowserText.setBackgroundResource(R.drawable.round);
        }
        mBrowserText.getBackground().setAlpha(75);

        mBrowserText.setOnClickListener(view -> {

            try {
                mActionMode = startSupportActionMode(
                        createSearchActionModeCallback());
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });

        if (!isDarkTheme()) {
            Window window = getWindow();
            window.setStatusBarColor(Color.BLACK);

            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                    decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); //set status text light
        }

        EventViewModel eventViewModel =
                new ViewModelProvider(this).get(EventViewModel.class);


        eventViewModel.getTheme().observe(this, (event) -> {

            if (event != null) {
                String content = event.getContent();
                if (!content.isEmpty()) {
                    try {
                        int color = Color.parseColor(content);
                        mAppBar.setBackgroundColor(color);


                        Window window = getWindow();
                        window.setStatusBarColor(color);

                        View decorView = window.getDecorView();
                        if (isDarkColor(color)) {
                            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); //set status text light
                            mBrowserText.setTextColor(Color.WHITE);
                            TextViewCompat.setCompoundDrawableTintList(mBrowserText,
                                    ColorStateList.valueOf(Color.WHITE));
                            mActionBookmark.setColorFilter(Color.WHITE);
                            mActionOverflow.setColorFilter(Color.WHITE);
                            mActionBookmarks.setColorFilter(Color.WHITE);
                            mActionIncognito.setColorFilter(Color.WHITE);
                            mProgressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
                        } else {
                            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); // set status text dark
                            mBrowserText.setTextColor(Color.BLACK);
                            TextViewCompat.setCompoundDrawableTintList(mBrowserText,
                                    ColorStateList.valueOf(Color.BLACK));
                            mActionBookmark.setColorFilter(Color.BLACK);
                            mActionOverflow.setColorFilter(Color.BLACK);
                            mActionBookmarks.setColorFilter(Color.BLACK);
                            mActionIncognito.setColorFilter(Color.BLACK);
                            mProgressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.BLACK));
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    }
                }
                eventViewModel.removeEvent(event);
            }


        });

        eventViewModel.getTor().observe(this, (event) -> {
            try {
                if (event != null) {
                    mActionIncognito.setVisibility(View.GONE);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        });

        eventViewModel.getError().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mWebView, content, Snackbar.LENGTH_LONG);
                        snackbar.show();
                    }
                    eventViewModel.removeEvent(event);

                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        });


        eventViewModel.getWarning().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mWebView, content,
                                Snackbar.LENGTH_SHORT);
                        snackbar.show();
                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        });


        eventViewModel.getInfo().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Toast.makeText(getApplicationContext(), content, Toast.LENGTH_SHORT).show();
                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        });

        mCustomWebChromeClient = new CustomWebChromeClient(this);
        mWebView.setWebChromeClient(mCustomWebChromeClient);


        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {

            try {

                String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                Uri uri = Uri.parse(url);

                if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                        Objects.equals(uri.getScheme(), Content.IPNS)) {
                    contentDownloader(uri);
                } else {
                    fileDownloader(uri, filename, mimeType, contentLength);
                }


            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        mWebView.setWebViewClient(new WebViewClient() {

            private final Map<Uri, Boolean> loadedUrls = new HashMap<>();
            private final SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);


            @Override
            public void onReceivedHttpError(
                    WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                LogUtils.info(TAG, "onReceivedHttpError " + errorResponse.getReasonPhrase());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                LogUtils.info(TAG, "onReceivedSslError " + error.toString());
            }


            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                LogUtils.info(TAG, "onPageCommitVisible " + url);
                mProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {

                try {
                    final String key = host.concat(realm);

                    String storedName = sharedPref.getString(key + "_name", null);
                    String storedPass = sharedPref.getString(key + "_pass", null);

                    LayoutInflater inflater = (LayoutInflater)
                            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    final View form = inflater.inflate(R.layout.http_auth_request, null);


                    final EditText usernameInput = form.findViewById(R.id.user_name);
                    final EditText passwordInput = form.findViewById(R.id.password);

                    if (storedName != null) {
                        usernameInput.setText(storedName);
                    }

                    if (storedPass != null) {
                        passwordInput.setText(storedPass);
                    }

                    AlertDialog.Builder authDialog = new AlertDialog
                            .Builder(MainActivity.this)
                            .setTitle(R.string.authentication)
                            .setView(form)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {

                                String username = usernameInput.getText().toString();
                                String password = passwordInput.getText().toString();


                                SharedPreferences.Editor editor = sharedPref.edit();
                                editor.putString(key + "_name", username);
                                editor.putString(key + "_pass", password);
                                editor.apply();

                                handler.proceed(username, password);
                                dialog.dismiss();
                            })

                            .setNegativeButton(android.R.string.cancel, (dialog, whichButton) -> {
                                dialog.dismiss();
                                view.stopLoading();
                                handler.cancel();
                            });


                    authDialog.show();
                    return;
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

                super.onReceivedHttpAuthRequest(view, handler, host, realm);
            }


            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                LogUtils.info(TAG, "onLoadResource : " + url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                LogUtils.info(TAG, "doUpdateVisitedHistory : " + url + " " + isReload);
            }

            @Override
            public void onPageStarted(WebView view, String uri, Bitmap favicon) {
                LogUtils.info(TAG, "onPageStarted : " + uri);

                mProgressBar.setVisibility(View.VISIBLE);
                invalidateMenu(docs.getOriginalUri(Uri.parse(uri)));
            }


            @Override
            public void onPageFinished(WebView view, String url) {
                LogUtils.info(TAG, "onPageFinished : " + url);
                if (Settings.THEME_ACTIVE && !isDarkTheme()) {
                    mWebView.loadUrl("javascript:window.CC_FUND.processHTML( (function (){var metas = document.getElementsByTagName('meta'); \n" +
                            "\n" +
                            "   for (var i=0; i<metas.length; i++) { \n" +
                            "      if (metas[i].getAttribute(\"name\") == \"theme-color\") { \n" +
                            "         return metas[i].getAttribute(\"content\"); \n" +
                            "      } \n" +
                            "   } \n" +
                            "\n" +
                            "    return \"\";})() );");
                }
                Uri uri = Uri.parse(url);
                if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                        Objects.equals(uri.getScheme(), Content.IPFS)) {

                    if (docs.numUris() == 0) {
                        mProgressBar.setVisibility(View.GONE);
                    }

                } else {
                    mProgressBar.setVisibility(View.GONE);
                }

            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                LogUtils.info(TAG, "onReceivedError " + view.getUrl() + " " + error.getDescription());

                mProgressBar.setVisibility(View.GONE);
            }


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {

                try {
                    Uri uri = request.getUrl();
                    LogUtils.info(TAG, "shouldOverrideUrlLoading : " + uri);

                    if (Objects.equals(uri.getScheme(), Content.ABOUT)) {
                        return true;
                    } else if (Objects.equals(uri.getScheme(), Content.HTTP) ||
                            Objects.equals(uri.getScheme(), Content.HTTPS)) {

                        Uri newUri = docs.redirectHttp(uri);
                        if (!Objects.equals(newUri, uri)) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, newUri,
                                    getApplicationContext(), MainActivity.class);
                            startActivity(intent);
                            return true;
                        }

                        return false;
                    } else if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                            Objects.equals(uri.getScheme(), Content.IPFS)) {

                        String res = uri.getQueryParameter("download");
                        if (Objects.equals(res, "1")) {
                            contentDownloader(uri);
                            return true;
                        }
                        mProgressBar.setVisibility(View.VISIBLE);
                        docs.attachUri(uri);
                        docs.releaseThreads();
                        return false;
                    } else if (Objects.equals(uri.getScheme(), Content.MAGNET)) {

                        MagnetUri magnetUri = MagnetUriParser.lenientParser().parse(uri.toString());

                        String name = uri.toString();
                        if (magnetUri.getDisplayName().isPresent()) {
                            name = magnetUri.getDisplayName().get();
                        }
                        magnetDownloader(uri, name);

                        return true;
                    } else {
                        // all other stuff
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            startActivity(intent);

                        } catch (Throwable throwable) {
                            LogUtils.error(TAG, throwable);
                        }
                        return true;
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

                return false;

            }


            public WebResourceResponse createRedirectMessage(@NonNull Uri uri) {
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(("<!DOCTYPE HTML>\n" +
                                "<html lang=\"en-US\">\n" +
                                "    <head>\n" +
                                "        <meta charset=\"UTF-8\">\n" +
                                "        <meta http-equiv=\"refresh\" content=\"0; url=" + uri.toString() + "\">\n" +
                                "        <script type=\"text/javascript\">\n" +
                                "            window.location.href = \"" + uri.toString() + "\"\n" +
                                "        </script>\n" +
                                "        <title>Page Redirection</title>\n" +
                                "    </head>\n" +
                                "    <body>\n" +
                                "        <!-- Note: don't tell people to `click` the link, just tell them that it is a link. -->\n" +
                                "        If you are not redirected automatically, follow this <a href='" + uri.toString() + "'>link to example</a>.\n" +
                                "    </body>\n" +
                                "</html>").getBytes()));
            }

            public WebResourceResponse createEmptyResource() {
                return new WebResourceResponse(MimeType.PLAIN_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream("".getBytes()));
            }

            public WebResourceResponse createErrorMessage(@NonNull Throwable exception) {
                String message = generateErrorHtml(exception);
                return new WebResourceResponse(MimeType.HTML_MIME_TYPE, Content.UTF8,
                        new ByteArrayInputStream(message.getBytes()));
            }


            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

                Uri uri = request.getUrl();
                LogUtils.info(TAG, "shouldInterceptRequest : " + uri.toString());

                if (Objects.equals(uri.getScheme(), Content.HTTP) ||
                        Objects.equals(uri.getScheme(), Content.HTTPS)) {
                    boolean ad;
                    if (!loadedUrls.containsKey(uri)) {
                        ad = AdBlocker.isAd(uri);
                        loadedUrls.put(uri, ad);
                    } else {
                        Boolean value = loadedUrls.get(uri);
                        Objects.requireNonNull(value);
                        ad = value;
                    }

                    if (ad) {
                        return createEmptyResource();
                    } else {

                        if (isOnion(uri) || PROXY.get()) {
                            try {

                                // todo why ???
                                String urlString = uri.toString().split("#")[0];

                                return ThorService.getProxyResponse(request, urlString);
                            } catch (Throwable throwable) {
                                LogUtils.error(TAG, throwable);
                                return createErrorMessage(throwable);
                            }
                        } else {
                            return null;
                        }
                    }

                } else if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                        Objects.equals(uri.getScheme(), Content.IPFS)) {

                    docs.bootstrap();

                    docs.connectUri(getApplicationContext(), uri);

                    Thread thread = Thread.currentThread();

                    docs.attachThread(thread.getId());

                    Closeable closeable = () -> !docs.shouldRun(thread.getId());
                    try {

                        Pair<Uri, Boolean> result = docs.redirectUri(uri, closeable);
                        Uri redirectUri = result.first;
                        if (!Objects.equals(uri, redirectUri)) {
                            docs.storeRedirect(redirectUri, uri);
                        }
                        if (result.second) {
                            return createRedirectMessage(redirectUri);
                        }
                        docs.connectUri(getApplicationContext(), redirectUri);

                        return docs.getResponse(getApplicationContext(), redirectUri, closeable);

                    } catch (Throwable throwable) {
                        if (closeable.isClosed()) {
                            return createEmptyResource();
                        }
                        if (throwable instanceof DOCS.ContentException) {
                            if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                                PageResolveWorker.resolve(getApplicationContext(), uri.getHost());
                            }
                        }

                        return createErrorMessage(throwable);
                    } finally {
                        docs.detachUri(uri);
                    }
                }
                return null;
            }
        });


        Intent intent = getIntent();

        boolean urlLoading = handleIntents(intent);

        if (savedInstanceState != null) {
            mWebView.restoreState(savedInstanceState);
        } else {
            if (!urlLoading) {
                openUri(Uri.parse(Settings.getDefaultHomepage()));
            }
        }

        LogUtils.info(InitApplication.TIME_TAG,
                "MainActivity finish onCreate [" + (System.currentTimeMillis() - start) + "]...");
    }

    private void download() {
        try {
            String url = mWebView.getUrl();
            if (url != null && !url.isEmpty()) {
                Uri uri = Uri.parse(url);
                if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                        Objects.equals(uri.getScheme(), Content.IPNS)) {
                    contentDownloader(uri);
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    private boolean downloadActive() {
        try {
            String url = mWebView.getUrl();
            if (url != null && !url.isEmpty()) {
                Uri uri = Uri.parse(url);
                if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                        Objects.equals(uri.getScheme(), Content.IPNS)) {
                    return true;
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    private void showDownloads(@NonNull Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType(MimeType.ALL);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            mFolderRequestForResult.launch(intent);

        } catch (Throwable e) {
            EVENTS.getInstance(getApplicationContext()).warning(
                    getString(R.string.no_activity_found_to_handle_uri));
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            if (mActionMode != null) {
                mActionMode.finish();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntents(intent);
    }

    @Override
    public void onBackPressed() {

        boolean result = onBackPressedCheck();
        if (result) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode != Activity.RESULT_OK) {
            return;
        }


        super.onActivityResult(requestCode, resultCode, data);

    }

    private boolean handleIntents(Intent intent) {

        final String action = intent.getAction();
        try {

            if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = intent.getData();
                if (uri != null) {
                    openUri(uri);
                    return true;
                }
            }

            if (Intent.ACTION_SEND.equals(action)) {
                if (Objects.equals(intent.getType(), MimeType.PLAIN_MIME_TYPE)) {
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    return doSearch(text);
                }
            }
            if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                String query =
                        intent.getStringExtra(SearchManager.QUERY);
                if (query == null) {
                    query = intent.getDataString();
                }
                return doSearch(query);
            }

            if (SHOW_DOWNLOADS.equals(intent.getAction())) {
                Uri uri = intent.getData();
                if (uri != null) {
                    showDownloads(uri);
                }
            }

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    private boolean doSearch(@Nullable String query) {
        try {

            if (mActionMode != null) {
                mActionMode.finish();
            }
            if (query != null && !query.isEmpty()) {
                Uri uri = Uri.parse(query);
                String scheme = uri.getScheme();
                if (Objects.equals(scheme, Content.IPNS) ||
                        Objects.equals(scheme, Content.IPFS) ||
                        Objects.equals(scheme, Content.HTTP) ||
                        Objects.equals(scheme, Content.HTTPS)) {
                    openUri(uri);
                } else {

                    IPFS ipfs = IPFS.getInstance(getApplicationContext());

                    String search = "https://duckduckgo.com/?q=" + query + "&kp=-1";
                    if (ipfs.isValidCID(query)) {
                        search = Content.IPFS + "://" + query;
                    }

                    openUri(Uri.parse(search));
                }
                return true;
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    public void openUri(@NonNull Uri uri) {

        try {
            invalidateMenu(uri);

            docs.cleanupResolver(uri);

            docs.releaseThreads();

            mWebView.stopLoading();

            mProgressBar.setVisibility(View.VISIBLE);

            mWebView.loadUrl(uri.toString());

            mAppBar.setExpanded(true, true);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }


    }

    @Override
    public WebView getWebView() {
        return mWebView;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        mWebView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mWebView.restoreState(savedInstanceState);
    }

    public String generateErrorHtml(@NonNull Throwable throwable) {

        return "<html>" +
                "<head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=2, user-scalable=yes\">" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">" +
                "<title>" + "Error" + "</title>" + "</head><body><div <div style=\"padding: 16px; word-break:break-word; background-color: #696969; color: white;\">" +
                throwable.getMessage() +
                "</div></body></html>";
    }

    private ActionMode.Callback createSearchActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_searchable, menu);

                mode.setCustomView(null);
                mode.setTitle("");
                mode.setTitleOptionalHint(true);

                MenuItem searchMenuItem = menu.findItem(R.id.action_search);
                SearchView mSearchView = (SearchView) searchMenuItem.getActionView();
                mSearchView.setMaxWidth(Integer.MAX_VALUE);
                SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
                mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

                TextView textView = mSearchView.findViewById(
                        androidx.appcompat.R.id.search_src_text);
                textView.setTextSize(14);

                ImageView magImage = mSearchView.findViewById(
                        androidx.appcompat.R.id.search_mag_icon);
                magImage.setVisibility(View.GONE);
                magImage.setImageDrawable(null);

                mSearchView.setIconifiedByDefault(false);
                mSearchView.setIconified(false);
                mSearchView.setSubmitButtonEnabled(false);
                mSearchView.setQueryHint(getString(R.string.enter_url));
                mSearchView.setFocusable(true);
                mSearchView.requestFocus();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
            }
        };

    }

    private boolean isOnion(@NonNull Uri uri) {
        try {
            return uri.getHost().endsWith(Content.ONION);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    private boolean isDarkColor(@ColorInt int color) {
        return ColorUtils.calculateLuminance(color) < 0.5;
    }

    private int getInverseColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        int alpha = Color.alpha(color);
        return Color.argb(alpha, 255 - red, 255 - green, 255 - blue);
    }

    private boolean isDarkTheme() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    public abstract static class AppBarStateChangedListener implements AppBarLayout.OnOffsetChangedListener {

        private State mCurrentState = State.IDLE;

        @Override
        public final void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            if (verticalOffset == 0) {
                setCurrentStateAndNotify(appBarLayout, State.EXPANDED);
            } else if (Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange()) {
                setCurrentStateAndNotify(appBarLayout, State.COLLAPSED);
            } else {
                setCurrentStateAndNotify(appBarLayout, State.IDLE);
            }
        }

        private void setCurrentStateAndNotify(AppBarLayout appBarLayout, State state) {
            if (mCurrentState != state) {
                onStateChanged(appBarLayout, state);
            }
            mCurrentState = state;
        }

        public abstract void onStateChanged(AppBarLayout appBarLayout, State state);

        public enum State {
            EXPANDED,
            COLLAPSED,
            IDLE
        }
    }

    private static class JsInterface {
        private final EVENTS events;

        public JsInterface(@NonNull Context context) {
            events = EVENTS.getInstance(context);
        }

        @JavascriptInterface
        @SuppressWarnings("unused")
        public void processHTML(String content) {
            if (content != null) {
                events.theme(content);
            }
        }
    }
}