package threads.thor.core.events;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;

public class EVENTS {

    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";
    public static final String INFO = "INFO";
    public static final String EXIT = "EXIT";
    public static final String PERMISSION = "PERMISSION";
    public static final String JAVASCRIPT = "JAVASCRIPT";
    private static EVENTS INSTANCE = null;
    private final EventsDatabase mEventsDatabase;

    private EVENTS(final EVENTS.Builder builder) {
        mEventsDatabase = builder.eventsDatabase;
    }

    @NonNull
    private static EVENTS createEvents(@NonNull EventsDatabase eventsDatabase) {

        return new EVENTS.Builder()
                .eventsDatabase(eventsDatabase)
                .build();
    }

    public static EVENTS getInstance(@NonNull Context context) {

        if (INSTANCE == null) {
            synchronized (EVENTS.class) {
                if (INSTANCE == null) {
                    EventsDatabase eventsDatabase =
                            Room.inMemoryDatabaseBuilder(context,
                                    EventsDatabase.class).
                                    allowMainThreadQueries().build();
                    INSTANCE = EVENTS.createEvents(eventsDatabase);
                }
            }
        }
        return INSTANCE;
    }

    @NonNull
    private Event createEvent(@NonNull String identifier, @NonNull String content) {

        return Event.createEvent(identifier, content);
    }

    private void storeEvent(@NonNull Event event) {
        mEventsDatabase.eventDao().insertEvent(event);
    }


    public void javascript() {
        storeEvent(createEvent(JAVASCRIPT, ""));
    }

    public void exit(@NonNull String content) {

        storeEvent(createEvent(EXIT, content));
    }

    public void error(@NonNull String content) {

        storeEvent(createEvent(ERROR, content));
    }

    public void permission(@NonNull String content) {

        storeEvent(createEvent(PERMISSION, content));
    }

    public void info(@NonNull String content) {

        storeEvent(createEvent(INFO, content));
    }

    public void warning(@NonNull String content) {

        storeEvent(createEvent(WARNING, content));
    }


    public EventsDatabase getEventsDatabase() {
        return mEventsDatabase;
    }


    static class Builder {
        EventsDatabase eventsDatabase = null;

        EVENTS build() {

            return new EVENTS(this);
        }

        Builder eventsDatabase(@NonNull EventsDatabase eventsDatabase) {

            this.eventsDatabase = eventsDatabase;
            return this;
        }
    }
}
