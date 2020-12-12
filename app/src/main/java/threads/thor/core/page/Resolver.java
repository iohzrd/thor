package threads.thor.core.page;


import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

@androidx.room.Entity
public class Resolver {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "name")
    private final String name;
    @NonNull
    @ColumnInfo(name = "content")
    private final String content;

    public Resolver(@NonNull String name, @NonNull String content) {
        this.name = name;
        this.content = content;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getContent() {
        return content;
    }

}
