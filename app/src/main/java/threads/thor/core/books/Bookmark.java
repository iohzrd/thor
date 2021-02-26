package threads.thor.core.books;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

import java.io.ByteArrayOutputStream;

@androidx.room.Entity
public class Bookmark {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "uri")
    private final String uri;
    @NonNull
    @ColumnInfo(name = "title")
    private final String title;
    @Deprecated
    @ColumnInfo(name = "sequence")
    private long sequence;
    @Nullable
    @ColumnInfo(name = "content")
    private String content;
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    private byte[] icon;
    @ColumnInfo(name = "timestamp")
    private long timestamp; // checked

    public Bookmark(@NonNull String uri, @NonNull String title) {
        this.uri = uri;
        this.title = title;
        this.timestamp = System.currentTimeMillis();
        this.sequence = 0L;
    }

    private static byte[] getBytes(@NonNull Bitmap bitmap) {
        Bitmap copy = bitmap.copy(bitmap.getConfig(), true);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        copy.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        copy.recycle();
        return byteArray;
    }

    long getSequence() {
        return sequence;
    }

    void setSequence(long sequence) {
        this.sequence = sequence;
    }

    @Nullable
    String getContent() {
        return content;
    }

    void setContent(@Nullable String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }


    public byte[] getIcon() {
        return icon;
    }


    void setIcon(byte[] icon) {
        this.icon = icon;
    }


    @Nullable
    public Bitmap getBitmapIcon() {
        if (icon != null) {
            return BitmapFactory.decodeByteArray(icon, 0, icon.length);
        }
        return null;
    }

    public void setBitmapIcon(@NonNull Bitmap bitmap) {
        setIcon(Bookmark.getBytes(bitmap));
    }

    @NonNull
    public String getUri() {
        return uri;
    }

    @NonNull
    public String getTitle() {
        return title;
    }
}
