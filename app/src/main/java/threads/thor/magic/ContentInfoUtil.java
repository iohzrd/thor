package threads.thor.magic;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;

import threads.thor.R;
import threads.thor.magic.entries.MagicEntries;


/**
 * <p>
 * Class which reads in the magic files and determines the {@link ContentInfo} for files and byte arrays. You use the
 * default constructor  to use the internal rules file or load in a local file from the
 * file-system using }. Once the rules are loaded, you can use
 * or other {@code findMatch(...)} methods to getData the content-type of a file or bytes.
 * </p>
 *
 * <pre>
 * // create a magic utility using the internal magic file
 * ContentInfoUtil util = new ContentInfoUtil();
 * // getData the content info for this file-path or null if no match
 * ContentInfo info = util.findMatch(&quot;/tmp/upload.tmp&quot;);
 * // display content type information
 * if (info == null) {
 * 	System.out.println(&quot;Unknown content-type&quot;);
 * } else {
 * 	// other information in ContentInfo type
 * 	System.out.println(&quot;Content-type is: &quot; + info.getName());
 * }
 * </pre>
 *
 * @author graywatson
 */
public class ContentInfoUtil {

    /**
     * Number of bytes that the utility class by default reads to determine the content type information.
     */
    private final static int DEFAULT_READ_SIZE = 10 * 1024;
    private static MagicEntries internalMagicEntries;
    private static ContentInfoUtil INSTANCE = null;
    private final MagicEntries magicEntries;

    /**
     * Construct a magic utility using the internal magic file built into the package. This also allows the caller to
     * log any errors discovered in the file(s).
     */
    private ContentInfoUtil(@NonNull Context context) {

        if (internalMagicEntries == null) {
            try {
                internalMagicEntries = readEntriesFromResource(context);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Could not load entries from internal magic file ", e);
            }
        }
        this.magicEntries = internalMagicEntries;
    }

    public static ContentInfoUtil getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (ContentInfoUtil.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContentInfoUtil(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Return the content type if the extension from the file-name matches our internal list. This can either be just
     * the extension part or it will look for the last period and take the string after that as the extension.
     *
     * @return The matching content-info or null if no matches.
     */
    @Nullable
    public static ContentInfo findExtensionMatch(String name) {
        name = name.toLowerCase();

        // look up the whole name first
        ContentType type = ContentType.fromFileExtension(name);
        if (type != ContentType.OTHER) {
            return new ContentInfo(type);
        }

        // now find the .ext part, if any
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) {
            return null;
        }

        type = ContentType.fromFileExtension(name.substring(index + 1));
        if (type == ContentType.OTHER) {
            return null;
        } else {
            return new ContentInfo(type);
        }
    }


    public ContentInfo findMatch(InputStream inputStream) throws IOException {
        byte[] bytes = new byte[DEFAULT_READ_SIZE];
        int numRead = inputStream.read(bytes);
        if (numRead < 0) {
            return null;
        }
        if (numRead < bytes.length) {
            // move the bytes into a smaller array
            bytes = Arrays.copyOf(bytes, numRead);
        }
        return findMatch(bytes);
    }

    /**
     * Return the content type from the associated bytes or null if none of the magic entries matched.
     */
    private ContentInfo findMatch(byte[] bytes) {
        if (bytes.length == 0) {
            return ContentInfo.EMPTY_INFO;
        } else {
            return magicEntries.findMatch(bytes);
        }
    }


    private MagicEntries readEntriesFromResource(@NonNull Context context) throws IOException {

        InputStream stream = context.getResources().openRawResource(R.raw.magic);

        Reader reader = null;
        try {
            reader = new InputStreamReader(new BufferedInputStream(stream));
            stream = null;
            return readEntries(reader);
        } finally {
            closeQuietly(reader);
            closeQuietly(stream);
        }
    }

    private MagicEntries readEntries(Reader reader) throws IOException {
        MagicEntries entries = new MagicEntries();
        readEntries(entries, reader);
        entries.optimizeFirstBytes();
        return entries;
    }

    private void readEntries(MagicEntries entries, Reader reader) throws IOException {
        BufferedReader lineReader = new BufferedReader(reader);
        try {
            entries.readEntries(lineReader, null);
        } finally {
            closeQuietly(lineReader);
        }
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // ignored
            }
        }
    }

    /**
     * Optional call-back which will be made whenever we discover an error while parsing the magic configuration files.
     * There are usually tons of badly formed lines and other errors.
     */
    public interface ErrorCallBack {

        /**
         * An error was generated while processing the line.
         *
         * @param line    Line where the error happened.
         * @param details Specific information about the error.
         * @param e       Exception that was thrown trying to parse the line or null if none.
         */
        void error(String line, String details, Exception e);
    }
}
