package threads.thor;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.net.http.SslError;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import androidx.work.WorkManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.ByteArrayInputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import threads.lite.IPFS;
import threads.lite.core.Closeable;
import threads.lite.core.TimeoutCloseable;
import threads.lite.format.Node;
import threads.lite.cid.PeerId;
import threads.LogUtils;
import threads.thor.core.Content;
import threads.thor.core.DOCS;
import threads.thor.core.books.BOOKS;
import threads.thor.core.books.Bookmark;
import threads.thor.core.events.EVENTS;
import threads.thor.core.events.EventViewModel;
import threads.thor.fragments.ActionListener;
import threads.thor.fragments.BookmarksDialogFragment;
import threads.thor.fragments.ContentDialogFragment;
import threads.thor.fragments.HistoryDialogFragment;
import threads.thor.fragments.SettingsDialogFragment;
import threads.thor.provider.FileDocumentsProvider;
import threads.thor.services.DiscoveryService;
import threads.thor.services.LocalConnectService;
import threads.thor.services.QRCodeService;
import threads.thor.services.RegistrationService;
import threads.thor.services.ThorService;
import threads.thor.utils.AdBlocker;
import threads.thor.utils.BookmarksAdapter;
import threads.thor.utils.CustomWebChromeClient;
import threads.thor.utils.MimeType;
import threads.thor.utils.PermissionAction;
import threads.thor.work.ClearBrowserDataWorker;
import threads.thor.work.DownloadContentWorker;
import threads.thor.work.DownloadFileWorker;


public class MainActivity extends AppCompatActivity implements
        ActionListener {

    public static final String SHOW_DOWNLOADS = "SHOW_DOWNLOADS";
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
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    invokeScan();
                } else {
                    EVENTS.getInstance(getApplicationContext()).permission(
                            getString(R.string.permission_camera_denied));
                }
            });
    private ConnectivityManager.NetworkCallback networkCallback;
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
    private final ActivityResultLauncher<Intent> mScanRequestForResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    IntentResult resultIntent = IntentIntegrator.parseActivityResult(
                            IntentIntegrator.REQUEST_CODE, result.getResultCode(), result.getData());
                    if (resultIntent != null) {
                        if (resultIntent.getContents() != null) {

                            try {
                                Uri uri = Uri.parse(resultIntent.getContents());
                                if (uri != null) {
                                    String scheme = uri.getScheme();
                                    if (Objects.equals(scheme, Content.IPNS) ||
                                            Objects.equals(scheme, Content.IPFS) ||
                                            Objects.equals(scheme, Content.HTTP) ||
                                            Objects.equals(scheme, Content.HTTPS)) {
                                        openUri(uri);
                                    } else {
                                        EVENTS.getInstance(getApplicationContext()).error(
                                                getString(R.string.codec_not_supported));
                                    }
                                } else {
                                    EVENTS.getInstance(getApplicationContext()).error(
                                            getString(R.string.codec_not_supported));
                                }
                            } catch (Throwable throwable) {
                                EVENTS.getInstance(getApplicationContext()).error(
                                        getString(R.string.codec_not_supported));
                            }
                        }
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }

            });
    private boolean hasCamera;
    private NsdManager mNsdManager;

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

    private void invokeScan() {
        try {
            PackageManager pm = getPackageManager();

            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                IntentIntegrator integrator = new IntentIntegrator(this);
                integrator.setOrientationLocked(false);
                Intent intent = integrator.createScanIntent();
                mScanRequestForResult.launch(intent);
            } else {
                EVENTS.getInstance(getApplicationContext()).permission(
                        getString(R.string.feature_camera_required));
            }
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
                mFindText.setBackgroundResource(android.R.color.transparent);
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
                } finally {
                    mActionMode = null;
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
                Uri uri = Uri.parse(url);
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

    private void updateUri(@NonNull Uri uri) {
        try {
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
                BOOKS books = BOOKS.getInstance(getApplicationContext());
                Bookmark bookmark = books.getBookmark(uri.toString());

                String title = uri.toString();
                if (bookmark != null) {
                    String bookmarkTitle = bookmark.getTitle();
                    if (!bookmarkTitle.isEmpty()) {
                        title = bookmarkTitle;
                    }
                }

                mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.lock, 0, 0, 0
                );
                mBrowserText.setText(title);
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

    private int border() {
        float density = getApplicationContext().getResources()
                .getDisplayMetrics().density;
        return Math.round((float) 48 * density);
    }

    @SuppressLint({"ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        PackageManager pm = getPackageManager();
        hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);

        docs = DOCS.getInstance(getApplicationContext());


        CoordinatorLayout mDrawerLayout = findViewById(R.id.drawer_layout);
        mAppBar = findViewById(R.id.appbar);
        mProgressBar = findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.GONE);
        mWebView = findViewById(R.id.web_view);
        mSwipeRefreshLayout = findViewById(R.id.swipe_container);

        mAppBar.addOnOffsetChangedListener(new AppBarStateChangedListener() {
            @Override
            public void onStateChanged(State state) {
                if (state == State.EXPANDED) {
                    mSwipeRefreshLayout.setEnabled(true);
                } else if (state == State.COLLAPSED) {
                    mSwipeRefreshLayout.setEnabled(false);
                }

            }
        });


        Settings.setWebSettings(mWebView, Settings.isJavascriptEnabled(getApplicationContext()));


        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(mWebView.getSettings(), WebSettingsCompat.FORCE_DARK_AUTO);
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, false);

        ImageButton mActionHome = findViewById(R.id.action_home);
        mActionHome.setOnClickListener(v -> {
            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                openUri(Uri.parse(Settings.HOMEPAGE));

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        ImageView mActionOverflow = findViewById(R.id.action_overflow);

        mActionOverflow.setOnClickListener(v -> {

            if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();

            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);


            View menuOverflow = inflater.inflate(
                    R.layout.menu_overflow, mDrawerLayout, false);


            PopupWindow dialog = new PopupWindow(
                    MainActivity.this, null, R.attr.popupMenuStyle);
            dialog.setContentView(menuOverflow);
            dialog.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.setOutsideTouchable(true);
            dialog.setFocusable(true);

            dialog.showAsDropDown(mActionOverflow, 0, -border(),
                    Gravity.TOP | Gravity.END);


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

                    mActionMode = startSupportActionMode(
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
                    Uri uri = Uri.parse(url);

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


            TextView actionInformation = menuOverflow.findViewById(R.id.action_information);
            actionInformation.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();

                    Uri uri = Uri.parse(mWebView.getUrl());

                    Uri uriImage = QRCodeService.getImage(getApplicationContext(), uri.toString());
                    ContentDialogFragment.newInstance(uriImage,
                            getString(R.string.url_access), uri.toString())
                            .show(getSupportFragmentManager(), ContentDialogFragment.TAG);


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


            TextView actionSettings = menuOverflow.findViewById(R.id.action_settings);
            actionSettings.setOnClickListener(v19 -> {
                try {
                    if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                        return;
                    }
                    mLastClickTime = SystemClock.elapsedRealtime();


                    SettingsDialogFragment dialogFragment = new SettingsDialogFragment();
                    dialogFragment.show(getSupportFragmentManager(), SettingsDialogFragment.TAG);
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
                Uri uri = Uri.parse(url);

                BOOKS books = BOOKS.getInstance(getApplicationContext());

                Bookmark bookmark = books.getBookmark(uri.toString());
                if (bookmark != null) {

                    String msg = bookmark.getTitle();
                    books.removeBookmark(bookmark);

                    if (msg.isEmpty()) {
                        msg = uri.toString();
                    }
                    EVENTS.getInstance(getApplicationContext()).warning(
                            getString(R.string.bookmark_removed, msg));
                } else {
                    Bitmap bitmap = mCustomWebChromeClient.getFavicon(uri);

                    String title = mCustomWebChromeClient.getTitle(uri);

                    bookmark = books.createBookmark(uri.toString(), title);
                    if (bitmap != null) {
                        bookmark.setBitmapIcon(bitmap);
                    }

                    books.storeBookmark(bookmark);

                    String msg = title;
                    if (msg.isEmpty()) {
                        msg = uri.toString();
                    }

                    EVENTS.getInstance(getApplicationContext()).warning(
                            getString(R.string.bookmark_added, msg));
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
        mBrowserText.setBackgroundResource(R.drawable.round);
        mBrowserText.getBackground().setAlpha(125);


        mBrowserText.setOnClickListener(view -> {

            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                mActionMode = startSupportActionMode(
                        createSearchActionModeCallback());
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        EventViewModel eventViewModel =
                new ViewModelProvider(this).get(EventViewModel.class);

        eventViewModel.getJavascript().observe(this, (event) -> {
            try {
                if (event != null) {

                    mWebView.getSettings().setJavaScriptEnabled(
                            Settings.isJavascriptEnabled(getApplicationContext()));

                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }
        });

        eventViewModel.getError().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mWebView, content, Snackbar.LENGTH_LONG);
                        snackbar.setActionTextColor(Color.WHITE);
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
                        snackbar.setActionTextColor(Color.WHITE);
                        snackbar.show();
                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }

        });


        eventViewModel.getExit().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_INDEFINITE);
                        snackbar.setActionTextColor(Color.WHITE);
                        snackbar.setAction(android.R.string.ok, (view) -> {

                            try {

                                WorkManager.getInstance(getApplicationContext()).cancelAllWork();

                                IPFS.getInstance(getApplicationContext()).shutdown();

                                finishAffinity();
                                System.exit(0);
                            } catch (Throwable e) {
                                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
                            } finally {
                                snackbar.dismiss();
                            }

                        });
                        snackbar.show();

                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
            }

        });


        eventViewModel.getPermission().observe(this, (event) -> {
            try {
                if (event != null) {
                    String content = event.getContent();
                    if (!content.isEmpty()) {
                        Snackbar snackbar = Snackbar.make(mDrawerLayout, content,
                                Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(R.string.settings, new PermissionAction());
                        snackbar.show();

                    }
                    eventViewModel.removeEvent(event);
                }
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
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
                    String res = uri.getQueryParameter("download");
                    if (Objects.equals(res, "0")) {
                        try {
                            Node node = docs.resolvePath(uri, new TimeoutCloseable(1));
                            Objects.requireNonNull(node);
                            Uri redirect = FileDocumentsProvider.getUriForIpfs(
                                    node, filename, mimeType);
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.putExtra(Intent.EXTRA_TITLE, filename);
                            intent.setDataAndType(redirect, mimeType);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        } catch (Throwable throwable) {
                            contentDownloader(uri);
                        } finally {
                            mProgressBar.setVisibility(View.GONE);
                        }
                    } else {
                        contentDownloader(uri);
                    }
                } else {
                    fileDownloader(uri, filename, mimeType, contentLength);
                }


            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        mWebView.setWebViewClient(new WebViewClient() {

            private final Map<Uri, Boolean> loadedUrls = new HashMap<>();
            private final AtomicReference<String> host = new AtomicReference<>();
            private final AtomicBoolean firstRun = new AtomicBoolean(true);

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

                    WebViewDatabase database = WebViewDatabase.getInstance(getApplicationContext());
                    String[] data = database.getHttpAuthUsernamePassword(host, realm);


                    String storedName = null;
                    String storedPass = null;

                    if (data != null) {
                        storedName = data[0];
                        storedPass = data[1];
                    }

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

                                database.setHttpAuthUsernamePassword(host, realm, username, password);

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
                releaseActionMode();
                updateUri(Uri.parse(uri));
            }


            @Override
            public void onPageFinished(WebView view, String url) {
                LogUtils.info(TAG, "onPageFinished : " + url);

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
            }


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {

                try {
                    Uri uri = request.getUrl();
                    LogUtils.debug(TAG, "shouldOverrideUrlLoading : " + uri);

                    if (!Objects.equals(host.get(), uri.getHost())) {
                        docs.releaseThreads();
                        docs.releaseContent();
                    }


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
                        return false;
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
                                "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                                "        <meta charset=\"UTF-8\">\n" +
                                "        <meta http-equiv=\"refresh\" content=\"0; url=" + uri.toString() + "\">\n" +
                                "        <title>Page Redirection</title>\n" +
                                "    </head>\n" +
                                "    <body>\n" +
                                "        Automatically redirected to the <a href='" + uri.toString() + "'>index.html</a> file\n" +
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
                LogUtils.debug(TAG, "shouldInterceptRequest : " + uri.toString());
                host.set(uri.getHost());
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
                        Uri redirectUri = docs.redirectHttps(uri);
                        if (!Objects.equals(redirectUri, uri)) {
                            return createRedirectMessage(redirectUri);
                        }
                        return null;
                    }

                } else if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                        Objects.equals(uri.getScheme(), Content.IPFS)) {

                    if (firstRun.getAndSet(false)) {
                        docs.bootstrap();
                    }

                    docs.attachUri(uri);

                    Thread thread = Thread.currentThread();

                    docs.attachThread(thread.getId());

                    Closeable closeable = () -> !docs.shouldRun(thread.getId());

                    try {

                        Uri redirectUri = docs.redirectUri(uri, closeable);
                        if (!Objects.equals(uri, redirectUri)) {
                            return createRedirectMessage(redirectUri);
                        }

                        return docs.getResponse(getApplicationContext(), redirectUri, closeable);

                    } catch (Throwable throwable) {
                        if (closeable.isClosed()) {
                            return createEmptyResource();
                        }
                        if (throwable instanceof DOCS.ContentException) {
                            if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                                LogUtils.error(TAG,
                                        "Content not found ... " + uri.toString());
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
                openUri(Uri.parse(Settings.HOMEPAGE));
            }
        }
        registerNetworkCallback();
        try {
            IPFS ipfs = IPFS.getInstance(getApplicationContext());
            PeerId peerId = ipfs.getPeerID();
            registerService(peerId.toBase58(), ipfs.getPort());
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        LogUtils.info(InitApplication.TIME_TAG,
                "MainActivity finish onCreate [" + (System.currentTimeMillis() - start) + "]...");
    }

    public void unRegisterNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    public void registerNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {

                    try {
                        ConnectivityManager connectivityManager =
                                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

                        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                        String interfaceName = null;
                        if(linkProperties != null) {
                            interfaceName = linkProperties.getInterfaceName();
                        }

                        IPFS ipfs = IPFS.getInstance(getApplicationContext());
                        if(interfaceName != null) {
                            ipfs.updateNetwork(interfaceName);
                        }
                        ipfs.bootstrap();
                    } catch (Throwable throwable){
                        LogUtils.error(TAG, throwable);
                    }
                }

                @Override
                public void onLost(Network network) {
                    try {
                        IPFS ipfs = IPFS.getInstance(getApplicationContext());
                        ipfs.reset();
                    } catch (Throwable throwable){
                        LogUtils.error(TAG, throwable);
                    }
                }
            };


            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            LogUtils.error(TAG, e);
        }
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

    private void releaseActionMode() {
        try {
            if (mActionMode != null) {
                mActionMode.finish();
                mActionMode = null;
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

            releaseActionMode();
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
            updateUri(uri);

            mProgressBar.setVisibility(View.VISIBLE);

            if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                    Objects.equals(uri.getScheme(), Content.IPFS)) {
                docs.attachUri(uri);
            }

            docs.releaseThreads();
            docs.releaseContent();

            mWebView.stopLoading();

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


                MenuItem scanMenuItem = menu.findItem(R.id.action_scan);
                if (!hasCamera) {
                    scanMenuItem.setVisible(false);
                }
                MenuItem searchMenuItem = menu.findItem(R.id.action_search);
                SearchView mSearchView = (SearchView) searchMenuItem.getActionView();
                mSearchView.setMaxWidth(Integer.MAX_VALUE);

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


                ListPopupWindow mPopupWindow = new ListPopupWindow(MainActivity.this,
                        null, R.attr.popupMenuStyle) {

                    @Override
                    public boolean isInputMethodNotNeeded() {
                        return true;
                    }
                };
                mPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                mPopupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
                mPopupWindow.setAnimationStyle(0);
                mPopupWindow.setModal(false);


                mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        mPopupWindow.dismiss();
                        doSearch(query);
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {

                        if (!newText.isEmpty()) {
                            BOOKS books = BOOKS.getInstance(getApplicationContext());
                            List<Bookmark> bookmarks = books.getBookmarksByQuery(newText);

                            if (!bookmarks.isEmpty()) {
                                mPopupWindow.setAdapter(new BookmarksAdapter(getApplicationContext(),
                                        new ArrayList<>(bookmarks)) {
                                    @Override
                                    public void onClick(@NonNull Bookmark bookmark) {
                                        try {
                                            openUri(Uri.parse(bookmark.getUri()));
                                        } catch (Throwable throwable) {
                                            LogUtils.error(TAG, throwable);
                                        } finally {
                                            mPopupWindow.dismiss();
                                            releaseActionMode();
                                        }
                                    }
                                });
                                mPopupWindow.setAnchorView(mSearchView);
                                mPopupWindow.show();
                                return true;
                            } else {
                                mPopupWindow.dismiss();
                            }
                        } else {
                            mPopupWindow.dismiss();
                        }

                        return false;
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
                if (item.getItemId() == R.id.action_scan) {
                    try {
                        if (SystemClock.elapsedRealtime() - mLastClickTime < 500) {
                            return false;
                        }
                        mLastClickTime = SystemClock.elapsedRealtime();


                        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED) {
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                            return false;
                        }

                        invokeScan();

                        if (hasCamera) {
                            IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
                            integrator.setOrientationLocked(false);
                            Intent intent = integrator.createScanIntent();
                            mScanRequestForResult.launch(intent);
                        } else {
                            EVENTS.getInstance(getApplicationContext()).permission(
                                    getString(R.string.feature_camera_required));
                        }
                    } catch (Throwable throwable) {
                        LogUtils.error(TAG, throwable);
                    } finally {
                        mode.finish();
                    }
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
            }
        };

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unRegisterNetworkCallback();
        releaseActionMode();
        try {
            if (mNsdManager != null) {
                mNsdManager.unregisterService(RegistrationService.getInstance());
                mNsdManager.stopServiceDiscovery(DiscoveryService.getInstance());
            }

        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    private void registerService(@NonNull String peerID, int port) {
        try {
            String serviceType = "_ipfs-discovery._udp";
            NsdServiceInfo serviceInfo = new NsdServiceInfo();
            serviceInfo.setServiceName(peerID);
            serviceInfo.setServiceType(serviceType);
            serviceInfo.setPort(port);
            mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            Objects.requireNonNull(mNsdManager);
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD,
                    RegistrationService.getInstance());


            DiscoveryService discovery = DiscoveryService.getInstance();
            discovery.setOnServiceFoundListener((info) -> mNsdManager.resolveService(info, new NsdManager.ResolveListener() {

                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {

                }


                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {

                    try {
                        String serviceName = serviceInfo.getServiceName();
                        boolean connect = !Objects.equals(peerID, serviceName);
                        if (connect) {
                            InetAddress inetAddress = serviceInfo.getHost();
                            LocalConnectService.connect(getApplicationContext(),
                                    serviceName, serviceInfo.getHost().toString(),
                                    serviceInfo.getPort(), inetAddress instanceof Inet6Address);
                        }

                    } catch (Throwable e) {
                        LogUtils.error(TAG, e);
                    }
                }
            }));
            mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
    }

    public abstract static class AppBarStateChangedListener implements AppBarLayout.OnOffsetChangedListener {

        private State mCurrentState = State.IDLE;

        @Override
        public final void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            if (verticalOffset == 0) {
                setCurrentStateAndNotify(State.EXPANDED);
            } else if (Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange()) {
                setCurrentStateAndNotify(State.COLLAPSED);
            } else {
                setCurrentStateAndNotify(State.IDLE);
            }
        }

        private void setCurrentStateAndNotify(State state) {
            if (mCurrentState != state) {
                onStateChanged(state);
            }
            mCurrentState = state;
        }

        public abstract void onStateChanged(State state);

        public enum State {
            EXPANDED,
            COLLAPSED,
            IDLE
        }
    }
}