package threads.thor.core.pages;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

@androidx.room.Entity
public class Page {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "pid")
    private final String pid;

    @Nullable
    @ColumnInfo(name = "content")
    private String content;

    @ColumnInfo(name = "sequence")
    private long sequence;
    @ColumnInfo(name = "rating")
    private long rating;
    @Nullable
    @ColumnInfo(name = "address")
    private String address;


    public Page(@NonNull String pid) {
        this.pid = pid;
        this.sequence = 0L;
        this.rating = 0L;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public long getRating() {
        return rating;
    }

    public void setRating(long rating) {
        this.rating = rating;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    public void setAddress(@Nullable String address) {
        this.address = address;
    }

    @NonNull
    public String getPid() {
        return pid;
    }

    @Nullable
    public String getContent() {
        return content;
    }

    public void setContent(@NonNull String content) {
        this.content = content;
    }


}