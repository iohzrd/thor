package threads.thor;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.LogUtils;
import threads.thor.bt.magnet.MagnetUri;
import threads.thor.bt.magnet.MagnetUriParser;
import threads.thor.core.Content;
import threads.thor.core.DOCS;
import threads.thor.core.events.EVENTS;
import threads.thor.core.events.EventViewModel;
import threads.thor.core.page.Bookmark;
import threads.thor.core.page.PAGES;
import threads.thor.core.page.Resolver;
import threads.thor.fragments.ActionListener;
import threads.thor.fragments.BookmarksDialogFragment;
import threads.thor.fragments.HistoryDialogFragment;
import threads.thor.ipfs.IPFS;
import threads.thor.services.ThorService;
import threads.thor.utils.AdBlocker;
import threads.thor.utils.CustomWebChromeClient;
import threads.thor.utils.FileDocumentsProvider;
import threads.thor.utils.MimeType;
import threads.thor.utils.Network;
import threads.thor.work.ClearCacheWorker;
import threads.thor.work.DownloadContentWorker;
import threads.thor.work.DownloadFileWorker;
import threads.thor.work.DownloadMagnetWorker;


public class MainActivity extends AppCompatActivity implements GestureDetector.OnGestureListener,
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
    private final AtomicBoolean showing = new AtomicBoolean(false);
    private final AtomicBoolean activeHide = new AtomicBoolean(false);

    private GestureDetector mGestureScanner;
    private WebView mWebView;
    private long mLastClickTime = 0;
    private TextView mBrowserText;
    private ActionMode mActionMode;
    private CustomWebChromeClient mCustomWebChromeClient;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private LinearLayout mProgressBar;
    private boolean isTablet;
    private ImageButton mActionNextPage;
    private ImageButton mActionPreviousPage;

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
            return true;
        }
        mLastClickTime = SystemClock.elapsedRealtime();


        int itemId = item.getItemId();
        if (itemId == R.id.action_share) {

            try {
                String uri = mWebView.getUrl();


                ComponentName[] names = {new ComponentName(getApplicationContext(), MainActivity.class)};

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_link));
                intent.putExtra(Intent.EXTRA_TEXT, uri);
                intent.setType(MimeType.PLAIN_MIME_TYPE);
                intent.putExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


                Intent chooser = Intent.createChooser(intent, getText(R.string.share));
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, names);
                startActivity(chooser);


            } catch (Throwable throwable) {
                EVENTS.getInstance(getApplicationContext()).warning(
                        getString(R.string.no_activity_found_to_handle_uri));
            }
            return true;
        } else if (itemId == R.id.action_bookmark) {


            try {
                String uri = mWebView.getUrl();

                Objects.requireNonNull(uri);

                PAGES pages = PAGES.getInstance(getApplicationContext());

                Bookmark bookmark = pages.getBookmark(uri);
                if (bookmark != null) {
                    String name = bookmark.getTitle();
                    pages.removeBookmark(bookmark);
                    EVENTS.getInstance(getApplicationContext()).warning(
                            getString(R.string.bookmark_removed, name));
                } else {
                    Bitmap bitmap = mCustomWebChromeClient.getFavicon(uri);

                    String title = mCustomWebChromeClient.getTitle(uri);

                    if (title == null) {
                        title = "" + mWebView.getTitle();
                    }

                    bookmark = pages.createBookmark(uri, title);
                    if (bitmap != null) {
                        bookmark.setBitmapIcon(bitmap);
                    }

                    String host = getHost(uri);
                    if (host != null) {
                        Resolver resolver = pages.getResolver(host);
                        if (resolver != null) {
                            bookmark.setContent(resolver.getContent());
                        }
                    }

                    pages.storeBookmark(bookmark);


                    EVENTS.getInstance(getApplicationContext()).warning(
                            getString(R.string.bookmark_added, title));
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            } finally {
                invalidateOptionsMenu();
            }
            return true;
        } else if (itemId == R.id.action_bookmarks) {


            BookmarksDialogFragment dialogFragment = new BookmarksDialogFragment();
            dialogFragment.show(getSupportFragmentManager(), BookmarksDialogFragment.TAG);

            return true;
        } else if (itemId == R.id.action_previous_page) {
            try {
                mWebView.goBack();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_next_page) {
            try {
                mWebView.goForward();
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_reload) {
            reload();
            return true;
        } else if (itemId == R.id.action_find_page) {


            try {
                startSupportActionMode(
                        createFindActionModeCallback());
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_downloads) {


            try {
                Uri root = Uri.parse(DOWNLOADS);
                showDownloads(root);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_history) {


            try {
                HistoryDialogFragment dialogFragment = new HistoryDialogFragment();
                dialogFragment.show(getSupportFragmentManager(), HistoryDialogFragment.TAG);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_cleanup) {


            try {


                mWebView.clearHistory();
                mWebView.clearCache(true);
                mWebView.clearFormData();

                // Clear all the cookies
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();

                IPFS.setCleanFlag(getApplicationContext(), true);
                ClearCacheWorker.clearCache(getApplicationContext());

                EVENTS.getInstance(getApplicationContext()).warning(
                        getString(R.string.clear_cache_and_cookies));

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_documentation) {


            try {
                String uri = "https://gitlab.com/remmer.wilts/thor";

                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri),
                        getApplicationContext(), MainActivity.class);
                startActivity(intent);
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        } else if (itemId == R.id.action_issues) {


            try {
                String uri = "https://gitlab.com/remmer.wilts/thor/issues";

                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri),
                        getApplicationContext(), MainActivity.class);
                startActivity(intent);
            } catch (Throwable e) {
                LogUtils.error(TAG, e);
            }

            return true;
        } else if (itemId == R.id.action_privacy_policy) {

            try {

                String data = ThorService.loadRawData(getApplicationContext(),
                        R.raw.privacy_policy);
                mWebView.loadData(data, MimeType.HTML_MIME_TYPE, Content.UTF8);

            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

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


            EVENTS.getInstance(getApplicationContext()).warning(name);
        });
        builder.setNeutralButton(getString(android.R.string.cancel),
                (dialog, which) -> dialog.cancel());
        builder.show();


    }

    private void contentDownloader(@NonNull Uri uri, @NonNull String name) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.download_title);
        builder.setMessage(name);

        builder.setPositiveButton(getString(android.R.string.yes), (dialog, which) -> {

            ThorService.setContentUri(getApplicationContext(), uri);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(DOWNLOADS));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mContentForResult.launch(intent);

            EVENTS.getInstance(getApplicationContext()).warning(name);
        });
        builder.setNeutralButton(getString(android.R.string.cancel),
                (dialog, which) -> dialog.cancel());
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

            EVENTS.getInstance(getApplicationContext()).warning(filename);

        });
        builder.setNeutralButton(getString(android.R.string.cancel),
                (dialog, which) -> dialog.cancel());
        builder.show();

    }

    public void reload() {

        try {
            mProgressBar.setVisibility(View.GONE);
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        try {
            preload(Uri.parse(mWebView.getUrl()));
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
                if (isTablet) {
                    mFindText.setMinWidth(400);
                    mFindText.setMaxWidth(600);
                } else {
                    mFindText.setMinWidth(200);
                    mFindText.setMaxWidth(400);
                }
                mFindText.setSingleLine();
                mFindText.setBackgroundResource(android.R.color.transparent);
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
            mWebView.goBack();
            return true;
        }

        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem mActionBookmark = menu.findItem(R.id.action_bookmark);
        mActionBookmark.setVisible(true);


        if (mWebView != null) {
            if (!isTablet) {
                menu.findItem(R.id.action_previous_page).setEnabled(mWebView.canGoBack());
                menu.findItem(R.id.action_next_page).setEnabled(mWebView.canGoForward());
            }

            PAGES pages = PAGES.getInstance(getApplicationContext());
            String uri = mWebView.getUrl();
            if (pages.hasBookmark(uri)) {
                mActionBookmark.setIcon(R.drawable.star);
            } else {
                mActionBookmark.setIcon(R.drawable.star_outline);
            }
        }
        return super.onCreateOptionsMenu(menu);

    }

    private void invalidateMenu(@Nullable Uri uri) {
        try {
            if (uri != null) {
                mBrowserText.setClickable(true);
                if (Objects.equals(uri.getScheme(), Content.HTTPS)) {
                    mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.lock, 0, 0, 0
                    );
                } else if (Objects.equals(uri.getScheme(), Content.HTTP)) {
                    mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.lock_open, 0, 0, 0
                    );
                } else {
                    mBrowserText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.battlenet, 0, 0, 0
                    );
                }
                mBrowserText.setCompoundDrawablePadding(8);

                mBrowserText.setText(uri.getHost());
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        try {
            if (mActionNextPage != null) {
                if (mWebView.canGoForward()) {
                    mActionNextPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                            android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
                    mActionNextPage.setEnabled(true);
                } else {
                    mActionNextPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                            android.R.color.darker_gray), android.graphics.PorterDuff.Mode.SRC_IN);
                    mActionNextPage.setEnabled(false);
                }
            }
            if (mActionPreviousPage != null) {
                if (mWebView.canGoBack()) {
                    mActionPreviousPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                            android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
                    mActionPreviousPage.setEnabled(true);
                } else {
                    mActionPreviousPage.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                            android.R.color.darker_gray), android.graphics.PorterDuff.Mode.SRC_IN);
                    mActionPreviousPage.setEnabled(false);
                }

            }
            invalidateOptionsMenu();
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }

    @SuppressLint({"ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar mToolbar = findViewById(R.id.toolbar);
        mToolbar.setContentInsetsAbsolute(0, 0);
        setTitle(null);

        isTablet = getResources().getBoolean(R.bool.isTablet);

        setSupportActionBar(mToolbar);
        mGestureScanner = new GestureDetector(getApplicationContext(), this);


        ImageButton actionIncognito = findViewById(R.id.action_incognito);

        mProgressBar = findViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.VISIBLE);

        mWebView = findViewById(R.id.web_view);

        ThorService.setWebSettings(mWebView);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, false);


        actionIncognito.setOnClickListener(v -> {
            try {
                if (SystemClock.elapsedRealtime() - mLastClickTime < CLICK_OFFSET) {
                    return;
                }
                mLastClickTime = SystemClock.elapsedRealtime();

                PROXY.set(!PROXY.get());

                if (!PROXY.get()) {
                    ThorService.setIncognitoMode(mWebView, false);

                    actionIncognito.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                            android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);

                } else {
                    ThorService.setIncognitoMode(mWebView, true);

                    actionIncognito.setColorFilter(ContextCompat.getColor(getApplicationContext(),
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
            ThorService.setIncognitoMode(mWebView, false);

            actionIncognito.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.white), android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            ThorService.setIncognitoMode(mWebView, true);

            actionIncognito.setColorFilter(ContextCompat.getColor(getApplicationContext(),
                    android.R.color.holo_green_light), android.graphics.PorterDuff.Mode.SRC_IN);
        }


        if (isTablet) {
            mActionNextPage = findViewById(R.id.action_next_page);
            mActionNextPage.setEnabled(mWebView.canGoForward());
            mActionNextPage.setOnClickListener(v -> mWebView.goForward());

            mActionPreviousPage = findViewById(R.id.action_previous_page);
            mActionPreviousPage.setEnabled(mWebView.canGoBack());
            mActionPreviousPage.setOnClickListener(v -> mWebView.goBack());


            ImageButton actionReload = findViewById(R.id.action_reload);
            actionReload.setOnClickListener(v -> reload());
        }

        mSwipeRefreshLayout = findViewById(R.id.swipe_container);
        if (!isTablet) {
            mSwipeRefreshLayout.setOnRefreshListener(() -> {
                try {
                    mSwipeRefreshLayout.setRefreshing(true);
                    reload();
                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
            });
            mSwipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_dark);
            mSwipeRefreshLayout.setProgressViewOffset(false, 100, 500);
        }

        mSwipeRefreshLayout.setEnabled(false);


        mBrowserText = findViewById(R.id.action_browser);


        mBrowserText.setOnClickListener(view -> {

            try {
                mActionMode = startSupportActionMode(
                        createSearchActionModeCallback());
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        });


        EventViewModel eventViewModel =
                new ViewModelProvider(this).get(EventViewModel.class);


        eventViewModel.getTor().observe(this, (event) -> {
            try {
                if (event != null) {
                    actionIncognito.setVisibility(View.GONE);
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


        if (!isTablet) {
            mWebView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY == 0 && oldScrollY > 0 && showing.get()) {
                    ActionBar bar = getSupportActionBar();
                    if (bar != null) {
                        if (bar.isShowing()) {
                            mSwipeRefreshLayout.setEnabled(false);
                            bar.hide();
                        }
                    }
                }
            });
        }
        if (!isTablet) {
            mWebView.setOnTouchListener((v, event) -> mGestureScanner.onTouchEvent(event));
        }

        mCustomWebChromeClient = new CustomWebChromeClient(this);
        mWebView.setWebChromeClient(mCustomWebChromeClient);


        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {

            try {

                String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
                Uri uri = Uri.parse(url);

                if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                        Objects.equals(uri.getScheme(), Content.IPNS)) {
                    contentDownloader(uri, ThorService.getFileName(uri));
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
                LogUtils.error(TAG, errorResponse.toString());

            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                LogUtils.error(TAG, error.toString());
                super.onReceivedSslError(view, handler, error);
            }


            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                LogUtils.error(TAG, "onPageCommitVisible " + url);

                mProgressBar.setVisibility(View.GONE);

                if (!isTablet) {
                    mSwipeRefreshLayout.setRefreshing(false);
                }
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
                LogUtils.error(TAG, "onLoadResource : " + url);
                super.onLoadResource(view, url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                LogUtils.error(TAG, "doUpdateVisitedHistory : " + url + " " + isReload);
                super.doUpdateVisitedHistory(view, url, isReload);

            }

            @Override
            public void onPageStarted(WebView view, String uri, Bitmap favicon) {
                LogUtils.error(TAG, "onPageStarted : " + uri);

                if (!isTablet) {
                    activeHide.set(true);
                }

                invalidateMenu(Uri.parse(uri));
            }


            @Override
            public void onPageFinished(WebView view, String url) {
                LogUtils.error(TAG, "onPageFinished : " + url);

                mProgressBar.setVisibility(View.GONE);

                if (!isTablet) {
                    mSwipeRefreshLayout.setRefreshing(false);

                    ActionBar bar = getSupportActionBar();
                    if (bar != null) {
                        if (bar.isShowing()) {
                            mSwipeRefreshLayout.setEnabled(false);
                            if (activeHide.get()) {
                                bar.hide();
                            }
                        }
                    }
                }

            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                LogUtils.error(TAG, "onReceivedError " + view.getUrl() + " " + error.getDescription());

                mProgressBar.setVisibility(View.GONE);

                if (!isTablet) {
                    mSwipeRefreshLayout.setRefreshing(false);
                }
            }


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {

                try {
                    Uri uri = request.getUrl();
                    LogUtils.error(TAG, "shouldOverrideUrlLoading : " + uri);

                    if (Objects.equals(uri.getScheme(), Content.HTTP) ||
                            Objects.equals(uri.getScheme(), Content.HTTPS)) {
                        return false;
                    } else if (Objects.equals(uri.getScheme(), Content.IPNS) ||
                            Objects.equals(uri.getScheme(), Content.IPFS)) {


                        String res = uri.getQueryParameter("download");
                        if (Objects.equals(res, "1")) {
                            contentDownloader(uri, ThorService.getFileName(uri));
                            return true;
                        }

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
                            EVENTS.getInstance(getApplicationContext()).warning(
                                    getString(R.string.no_activity_found_to_handle_uri));
                        }
                        return true;
                    }

                } catch (Throwable throwable) {
                    LogUtils.error(TAG, throwable);
                }
                return false;

            }


            public WebResourceResponse createEmptyResource() {
                return new WebResourceResponse("text/plain", Content.UTF8,
                        new ByteArrayInputStream("".getBytes()));
            }

            public WebResourceResponse createErrorMessage(@NonNull Throwable exception) {
                String message = generateErrorHtml(exception);
                return new WebResourceResponse("text/html", Content.UTF8,
                        new ByteArrayInputStream(message.getBytes()));
            }


            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {


                Uri uri = request.getUrl();
                LogUtils.error(TAG, "shouldInterceptRequest : " + uri.toString());

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

                    try {
                        DOCS docs = DOCS.getInstance(getApplicationContext());
                        {
                            Uri newUri = docs.invalidUri(uri);
                            if (!Objects.equals(uri, newUri)) {
                                return createEmptyResource();
                            }
                        }
                        boolean online = Network.isConnected(getApplicationContext());

                        if (online) {
                            docs.connectUri(uri);
                        }

                        return docs.getResponse(uri, online, 30000);

                    } catch (Throwable throwable) {
                        return createErrorMessage(throwable);
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
                openUri(Uri.parse(ThorService.getDefaultHomepage()));
            }
        }


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
                    return true;
                } else {

                    IPFS ipfs = IPFS.getInstance(getApplicationContext());

                    String search = "https://duckduckgo.com/?q=" + query + "&kp=-1";
                    if (ipfs.isValidCID(query)) {
                        search = Content.IPFS + "://" + query;
                    }

                    openUri(Uri.parse(search));
                    return true;
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return false;
    }

    @Override
    public void openUri(@NonNull Uri uri) {

        mProgressBar.setVisibility(View.VISIBLE);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            if (!bar.isShowing()) {
                bar.show();
            }
        }

        invalidateMenu(uri);

        preload(uri);

        mWebView.stopLoading();
        mWebView.loadUrl(uri.toString());


    }

    private void preload(@NonNull Uri uri) {

        if (Objects.equals(uri.getScheme(), Content.IPFS) ||
                Objects.equals(uri.getScheme(), Content.IPNS)) {
            try {

                if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                    PAGES pages = PAGES.getInstance(getApplicationContext());
                    String name = uri.getHost();
                    if (name != null) {
                        pages.removeResolver(name);
                    }
                }


            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
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

                mSearchView.requestFocus();
                mSearchView.setIconifiedByDefault(false);
                mSearchView.setIconified(false);
                mSearchView.setSubmitButtonEnabled(false);
                mSearchView.setQueryHint(getString(R.string.enter_url));
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

    @Nullable
    private String getHost(@NonNull String url) {
        try {
            Uri uri = Uri.parse(url);
            if (Objects.equals(uri.getScheme(), Content.IPNS)) {
                return uri.getHost();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
        return null;
    }


    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

        if (distanceY > 20) {
            activeHide.set(false);
            showing.set(false);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                if (bar.isShowing()) {
                    mSwipeRefreshLayout.setEnabled(false);
                    mProgressBar.setVisibility(View.GONE);
                    bar.hide();
                }
            }
        } else if (distanceY < -20) {
            activeHide.set(false);
            ActionBar bar = getSupportActionBar();
            if (bar != null) {
                if (!bar.isShowing()) {
                    mSwipeRefreshLayout.setEnabled(mWebView.getScrollY() == 0);
                    bar.show();
                } else {
                    showing.set(true);
                }
            }
        }

        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }


}