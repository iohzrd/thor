package threads.thor.magic;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * Information associated with some content, returned by the magic matching code in
 * and other methods.
 *
 * @author graywatson
 */
public class ContentInfo implements Serializable {

    public static final ContentInfo EMPTY_INFO = new ContentInfo(ContentType.EMPTY);
    private static final long serialVersionUID = 1342819252130963539L;
    private final ContentType contentType;
    private final String name;
    private final String message;
    private final String mimeType;
    private final boolean partial;

    public ContentInfo(String name, String mimeType, String message, boolean partial) {
        this.contentType = ContentType.fromMimeType(mimeType);
        if (this.contentType == ContentType.OTHER) {
            this.name = name;
        } else {
            this.name = this.contentType.getSimpleName();
        }
        this.mimeType = mimeType;
        this.message = message;
        this.partial = partial;
    }

    ContentInfo(ContentType contentType) {
        this.contentType = contentType;
        this.name = contentType.getSimpleName();
        this.mimeType = contentType.getMimeType();
        this.message = null;
        this.partial = false;
    }

    /**
     * Returns the mime-type or null if none.
     */
    public String getMimeType() {
        return mimeType;
    }


    /**
     * Whether or not this was a partial match. For some of the types, there is a main matching pattern and then more
     * specific patterns which detect additional features of the type. A partial match means that none of the more
     * specific patterns fully matched the content. It's probably still of the type but just not a variant that the
     * entries from the magic file(s) know about.
     */
    public boolean isPartial() {
        return partial;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (contentType != null) {
            sb.append(", type ").append(contentType);
        }
        if (mimeType != null) {
            sb.append(", mime '").append(mimeType).append('\'');
        }
        if (message != null) {
            sb.append(", msg '").append(message).append('\'');
        }
        return sb.toString();
    }
}
