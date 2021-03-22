package threads.thor.services;

import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import threads.LogUtils;
import threads.thor.magic.ContentInfo;
import threads.thor.magic.ContentInfoUtil;
import threads.thor.utils.MimeType;

public class MimeTypeService {
    public static final String PDF_MIME_TYPE = "application/pdf";
    public static final String OCTET_MIME_TYPE = "application/octet-stream";
    public static final String AUDIO = "audio";
    public static final String TEXT = "text";
    public static final String VIDEO = "video";
    public static final String IMAGE = "image";
    public static final String APPLICATION = "application";
    public static final String SVG_FOLDER = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"  version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\" d=\"M10,4H4C2.89,4 2,4.89 2,6V18C2,19.1 2.9,20 4,20H20C21.1,20 22,19.1 22,18V8C22,6.89 21.1,6 20,6H12L10,4Z\" /></svg>";
    private static final String TAG = MimeTypeService.class.getSimpleName();
    private static final String SVG_DOWNLOAD = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\" d=\"M5,20H19V18H5M19,9H15V3H9V9H5L12,16L19,9Z\" /></svg>";
    private static final String SVG_OCTET = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"  version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\" d=\"M14,2H6C4.89,2 4,2.9 4,4V20C4,21.1 4.9,22 6,22H18C19.1,22 20,21.1 20,20V8L14,2M14.5,18.9L12,17.5L9.5,19L10.2,16.2L8,14.3L10.9,14.1L12,11.4L13.1,14L16,14.2L13.8,16.1L14.5,18.9M13,9V3.5L18.5,9H13Z\" /></svg>";
    private static final String SVG_TEXT = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\" d=\"M6,2C4.9,2 4,2.9 4,4V20C4,21.1 4.9,22 6,22H18C19.1,22 20,21.1 20,20V8L14,2H6M6,4H13V9H18V20H6V4M8,12V14H16V12H8M8,16V18H13V16H8Z\" /></svg>";
    private static final String SVG_APPLICATION = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\" d=\"M19,4C20.11,4 21,4.9 21,6V18C21,19.1 20.1,20 19,20H5C3.89,20 3,19.1 3,18V6C3,4.9 3.9,4 5,4H19M19,18V8H5V18H19Z\" /></svg>";
    private static final String SVG_PDF = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\" d=\"M12,10.5H13V13.5H12V10.5M7,11.5H8V10.5H7V11.5M20,6V18C20,19.1 19.1,20 18,20H6C4.9,20 4,19.1 4,18V6C4,4.9 4.9,4 6,4H18C19.1,4 20,4.9 20,6M9.5,10.5C9.5,9.67 8.83,9 8,9H5.5V15H7V13H8C8.83,13 9.5,12.33 9.5,11.5V10.5M14.5,10.5C14.5,9.67 13.83,9 13,9H10.5V15H13C13.83,15 14.5,14.33 14.5,13.5V10.5M18.5,9H15.5V15H17V13H18.5V11.5H17V10.5H18.5V9Z\" /></svg>";
    private static final String SVG_MOVIE = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\" d=\"M5.76,10H20V18H4V6.47M22,4H18L20,8H17L15,4H13L15,8H12L10,4H8L10,8H7L5,4H4C2.9,4 2,4.9 2,6V18C2,19.1 2.9,20 4,20H20C21.1,20 22,19.1 22,18V4Z\" /></svg>";
    private static final String SVG_IMAGE = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\"  d=\"M19,19H5V5H19M19,3H5C3.9,3 3,3.9 3,5V19C3,20.1 3.9,21 5,21H19C20.1,21 21,20.1 21,19V5C21,3.9 20.1,3 19,3M13.96,12.29L11.21,15.83L9.25,13.47L6.5,17H17.5L13.96,12.29Z\" /></svg>";
    private static final String SVG_AUDIO = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path fill=\"#696969\"  d=\"M14,3.23V5.29C16.89,6.15 19,8.83 19,12C19,15.17 16.89,17.84 14,18.7V20.77C18,19.86 21,16.28 21,12C21,7.72 18,4.14 14,3.23M16.5,12C16.5,10.23 15.5,8.71 14,7.97V16C15.5,15.29 16.5,13.76 16.5,12M3,9V15H7L12,20V4L7,9H3Z\" /></svg>";

    public static String getSvgDownload() {
        return SVG_DOWNLOAD;
    }

    @NonNull
    public static String getSvgResource(@NonNull String name) {
        String mimeType = getMimeTypeDefaultDir(name);

        if (!mimeType.isEmpty()) {
            if (mimeType.equals(DocumentsContract.Document.MIME_TYPE_DIR)) {
                return SVG_FOLDER;
            }
            if (mimeType.equals(OCTET_MIME_TYPE)) {
                return SVG_OCTET;
            }
            if (mimeType.equals(PDF_MIME_TYPE)) {
                return SVG_PDF;
            }
            if (mimeType.startsWith(TEXT)) {
                return SVG_TEXT;
            }
            if (mimeType.startsWith(VIDEO)) {
                return SVG_MOVIE;
            }
            if (mimeType.startsWith(IMAGE)) {
                return SVG_IMAGE;
            }
            if (mimeType.startsWith(AUDIO)) {
                return SVG_AUDIO;
            }
            if (mimeType.startsWith(APPLICATION)) {
                return SVG_APPLICATION;
            }
        }
        return SVG_OCTET;
    }

    @NonNull
    private static String getMimeTypeDefaultDir(@NonNull String name) {
        String mimeType = evaluateMimeType(name);
        if (mimeType != null) {
            return mimeType;
        }
        try {
            ContentInfo info = ContentInfoUtil.findExtensionMatch(name);
            if (info != null) {
                return info.getMimeType();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        return MimeType.DIR_MIME_TYPE;
    }


    @NonNull
    public static String getMimeType(@NonNull String name) {
        String mimeType = evaluateMimeType(name);
        if (mimeType != null) {
            return mimeType;
        }
        try {
            ContentInfo info = ContentInfoUtil.findExtensionMatch(name);
            if (info != null) {
                return info.getMimeType();
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }

        return MimeType.OCTET_MIME_TYPE;
    }

    public static Optional<String> getExtension(@Nullable String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }


    @Nullable
    private static String evaluateMimeType(@NonNull String filename) {
        try {
            Optional<String> extension = getExtension(filename);
            if (extension.isPresent()) {
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.get());
                if (mimeType != null) {
                    return mimeType;
                }
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
        }
        return null;
    }
}
