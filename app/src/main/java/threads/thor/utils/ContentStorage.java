package threads.thor.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import threads.LogUtils;
import threads.thor.FileProvider;
import threads.thor.magnet.data.Storage;
import threads.thor.magnet.data.StorageUnit;
import threads.thor.magnet.event.EventBus;
import threads.thor.magnet.metainfo.Torrent;
import threads.thor.magnet.metainfo.TorrentFile;
import threads.thor.services.MimeTypeService;

public class ContentStorage implements Storage {


    private static final String TAG = ContentStorage.class.getSimpleName();

    private final DocumentFile root;
    private final EventBus eventBus;
    private final Context context;
    private final FileProvider fileProvider;
    private final List<ContentStorageUnit> units = new ArrayList<>();

    public ContentStorage(@NonNull Context context, @NonNull EventBus eventbus, @NonNull DocumentFile root) {
        this.context = context;
        this.eventBus = eventbus;
        this.root = root;
        this.fileProvider = FileProvider.getInstance(context);
    }

    @NonNull
    File getDataDir() {
        return fileProvider.getDataDir();
    }

    @NonNull
    Context getContext() {
        return context;
    }


    @NonNull
    EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public StorageUnit getUnit(@NonNull Torrent torrent, @NonNull TorrentFile torrentFile) {

        try {
            DocumentFile child = root;

            List<String> paths = torrentFile.getPathElements();
            long elements = paths.size();
            for (int i = 0; i < elements; i++) {
                String path = PathNormalizer.normalize(paths.get(i));

                Objects.requireNonNull(child);

                DocumentFile find = child.findFile(path);
                if (i < (elements - 1)) {

                    if (find != null && find.exists() && find.isDirectory()) {
                        child = find;
                    } else {
                        child = child.createDirectory(path);
                    }

                } else {
                    if (find != null && find.exists() && !find.isDirectory()) {
                        child = find;
                    } else {
                        String mimeType = MimeTypeService.getMimeType(path);

                        child = child.createFile(mimeType, path);
                    }
                }
            }
            Objects.requireNonNull(child);
            ContentStorageUnit unit = new ContentStorageUnit(this, torrentFile, child);
            units.add(unit);
            return unit;
        } catch (Throwable e) {
            LogUtils.error(TAG, e);
            throw new RuntimeException(e);
        }
    }

    public void finish() {
        try {
            for (ContentStorageUnit unit : units) {
                if (unit.isComplete()) {
                    while (!unit.isFinished()) {
                        Thread.sleep(500);
                    }
                    if (unit.isFinished()) {
                        boolean deleted = unit.getFile().delete();
                        LogUtils.error(TAG, unit.getFile().getAbsolutePath() + " " + deleted);
                    }
                }
            }
        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
        }
    }


    static class PathNormalizer {
        private static final String separator = File.separator;


        static String normalize(String path) {
            String normalized = path.trim();
            if (normalized.isEmpty()) {
                return "_";
            }

            StringTokenizer tokenizer = new StringTokenizer(normalized, separator, true);
            StringBuilder buf = new StringBuilder(normalized.length());
            boolean first = true;
            while (tokenizer.hasMoreTokens()) {
                String element = tokenizer.nextToken();
                if (separator.equals(element)) {
                    if (first) {
                        buf.append("_");
                    }
                    buf.append(separator);
                    // this will handle inner slash sequences, like ...a//b...
                    first = true;
                } else {
                    buf.append(normalizePathElement(element));
                    first = false;
                }
            }

            normalized = buf.toString();
            return replaceTrailingSlashes(normalized);
        }

        private static String normalizePathElement(String pathElement) {
            // truncate leading and trailing whitespaces
            String normalized = pathElement.trim();
            if (normalized.isEmpty()) {
                return "_";
            }

            // truncate trailing whitespaces and dots;
            // this will also eliminate '.' and '..' relative names
            char[] value = normalized.toCharArray();
            int to = value.length;
            while (to > 0 && (value[to - 1] == '.' || value[to - 1] == ' ')) {
                to--;
            }
            if (to == 0) {
                normalized = "";
            } else if (to < value.length) {
                normalized = normalized.substring(0, to);
            }

            return normalized.isEmpty() ? "_" : normalized;
        }

        private static String replaceTrailingSlashes(String path) {
            if (path.isEmpty()) {
                return path;
            }

            int k = 0;
            while (path.endsWith(separator)) {
                path = path.substring(0, path.length() - separator.length());
                k++;
            }
            if (k > 0) {
                char[] separatorChars = separator.toCharArray();
                char[] value = new char[path.length() + (separatorChars.length + 1) * k];
                System.arraycopy(path.toCharArray(), 0, value, 0, path.length());
                for (int offset = path.length(); offset < value.length; offset += separatorChars.length + 1) {
                    System.arraycopy(separatorChars, 0, value, offset, separatorChars.length);
                    value[offset + separatorChars.length] = '_';
                }
                path = new String(value);
            }

            return path;
        }
    }
}
