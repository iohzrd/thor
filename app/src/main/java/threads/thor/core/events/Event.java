package threads.thor.core.events;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Event {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "identifier")
    private final String identifier;
    @NonNull
    @ColumnInfo(name = "content")
    private final String content;

    Event(@NonNull String identifier, @NonNull String content) {

        this.identifier = identifier;
        this.content = content;
    }

    public static Event createEvent(@NonNull String identifier, @NonNull String content) {
        return new Event(identifier, content);
    }

    @NonNull
    public String getContent() {
        return content;
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }


}
