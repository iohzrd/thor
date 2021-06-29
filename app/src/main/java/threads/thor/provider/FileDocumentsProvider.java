package threads.thor.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;


import threads.LogUtils;
import threads.lite.IPFS;
import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.format.Node;
import threads.lite.utils.Reader;
import threads.thor.BuildConfig;
import threads.thor.R;
import threads.thor.utils.MimeType;

public class FileDocumentsProvider extends DocumentsProvider {
    public static final String SCHEME = "content";
    private static final String TAG = FileDocumentsProvider.class.getSimpleName();
    private static final String DOCUMENT = "document";
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
    };
    private static final Hashtable<String, CidInfo> CID_INFO_HASHTABLE = new Hashtable<>();
    private String appName;
    private IPFS ipfs;
    private StorageManager mStorageManager;

    public static boolean isPartial(@NonNull Context context, @NonNull Uri uri) {
        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor cursor = contentResolver.query(uri, new String[]{
                Document.COLUMN_FLAGS}, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();

            int docFlags = cursor.getInt(0);
            if ((docFlags & Document.FLAG_PARTIAL) != 0) {
                return true;
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }
        return false;
    }

    private static String[] resolveRootProjection(String[] projection) {
        String[] DEFAULT_ROOT_PROJECTION = new String[]{
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_ICON,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_SUMMARY,
                DocumentsContract.Root.COLUMN_FLAGS,

        };
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    @SuppressWarnings("SameReturnValue")
    @NonNull
    private static String getRoot() {
        return "0";
    }

    public static boolean hasReadPermission(@NonNull Context context, @NonNull Uri uri) {
        int perm = context.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return perm != PackageManager.PERMISSION_DENIED;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasWritePermission(@NonNull Context context, @NonNull Uri uri) {
        int perm = context.checkUriPermission(uri, Binder.getCallingPid(), Binder.getCallingUid(),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return perm != PackageManager.PERMISSION_DENIED;
    }

    @NonNull
    public static String getFileName(@NonNull Context context, @NonNull Uri uri) {
        String filename = null;

        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor cursor = contentResolver.query(uri,
                null, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            filename = cursor.getString(nameIndex);
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }

        if (filename == null) {
            filename = uri.getLastPathSegment();
        }

        if (filename == null) {
            filename = "file_name_not_detected";
        }

        return filename;
    }

    @NonNull
    public static String getMimeType(@NonNull Context context, @NonNull Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            mimeType = MimeType.OCTET_MIME_TYPE;
        }
        return mimeType;
    }

    public static long getFileSize(@NonNull Context context, @NonNull Uri uri) {

        ContentResolver contentResolver = context.getContentResolver();

        try (Cursor cursor = contentResolver.query(uri,
                null, null, null, null)) {

            Objects.requireNonNull(cursor);
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            return cursor.getLong(nameIndex);
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }


        try (ParcelFileDescriptor fd = contentResolver.openFileDescriptor(uri, "r")) {
            Objects.requireNonNull(fd);
            return fd.getStatSize();
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }
        return -1;
    }

    public static Uri getUriForIpfs(@NonNull Node node, @NonNull String name, @NonNull String mimeType) {

        CID_INFO_HASHTABLE.put(node.getCid().String(), new CidInfo(name, mimeType, node.size()));

        Uri.Builder builder = new Uri.Builder();
        builder.scheme(SCHEME)
                .authority(BuildConfig.DOCUMENTS_AUTHORITY)
                .appendPath(DOCUMENT)
                .appendPath(node.getCid().String());

        return builder.build();
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));

        String rootId = BuildConfig.DOCUMENTS_AUTHORITY;
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, rootId);
        row.add(DocumentsContract.Root.COLUMN_ICON, R.drawable.app_icon);
        row.add(DocumentsContract.Root.COLUMN_TITLE, appName);
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_LOCAL_ONLY);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getRoot());
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        LogUtils.info(TAG, "queryDocument : " + documentId);
        MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));

        try {

            if (ipfs.isValidCID(documentId)) {
                CidInfo info = CID_INFO_HASHTABLE.get(documentId);
                Objects.requireNonNull(info);

                MatrixCursor.RowBuilder row = result.newRow();
                row.add(Document.COLUMN_DOCUMENT_ID, documentId);
                row.add(Document.COLUMN_DISPLAY_NAME, info.name);
                row.add(Document.COLUMN_SIZE, info.size);
                row.add(Document.COLUMN_MIME_TYPE, info.mimeType);
                row.add(Document.COLUMN_LAST_MODIFIED, new Date());

            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            throw new FileNotFoundException("" + throwable.getLocalizedMessage());
        }

        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        return null;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
                                             @Nullable CancellationSignal signal) throws FileNotFoundException {

        LogUtils.info(TAG, "openDocument : " + documentId);

        try {
            final boolean isWrite = (mode.indexOf('w') != -1);
            if (!isWrite) {
                final AtomicBoolean release = new AtomicBoolean(false);
                Closeable closeable = release::get;
                if (signal != null) {
                    closeable = signal::isCanceled;
                }
                final Reader reader = ipfs.getReader(Cid.decode(documentId), closeable);
                Handler handler = new Handler(getContext().getMainLooper());

                return mStorageManager.openProxyFileDescriptor(
                        ParcelFileDescriptor.parseMode(mode),
                        new ProxyFileDescriptorCallback() {

                            public long onGetSize() {
                                return reader.getSize();
                            }

                            public int onRead(long offset, int size, byte[] data) throws ErrnoException {
                                try {
                                    return reader.readNextData(offset, size, data);
                                } catch (Throwable throwable) {
                                    LogUtils.error(TAG, throwable);
                                    throw new ErrnoException(throwable.getLocalizedMessage(), OsConstants.EBADF);
                                }
                            }

                            @Override
                            public void onRelease() {
                                release.set(true);
                            }
                        }, handler);

            }
        } catch (Throwable throwable) {
            throw new FileNotFoundException("" + throwable.getLocalizedMessage());
        }
        throw new FileNotFoundException();
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        Objects.requireNonNull(context);
        appName = context.getString(R.string.app_name);
        mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        ipfs = IPFS.getInstance(context);
        return true;
    }

    private static class CidInfo {
        private final String name;
        private final String mimeType;
        private final long size;

        private CidInfo(String name, String mimeType, long size) {
            this.name = name;
            this.mimeType = mimeType;
            this.size = size;
        }
    }

}