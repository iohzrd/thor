package threads.thor.core.events;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

public class EventViewModel extends AndroidViewModel {

    private final EventsDatabase eventsDatabase;

    public EventViewModel(@NonNull Application application) {
        super(application);
        eventsDatabase = EVENTS.getInstance(
                application.getApplicationContext()).getEventsDatabase();
    }

    public LiveData<Event> getError() {
        return eventsDatabase.eventDao().getEvent(EVENTS.ERROR);
    }

    public LiveData<Event> getWarning() {
        return eventsDatabase.eventDao().getEvent(EVENTS.WARNING);
    }

    public LiveData<Event> getInfo() {
        return eventsDatabase.eventDao().getEvent(EVENTS.INFO);
    }


    public void removeEvent(@NonNull final Event event) {
        new Thread(() -> eventsDatabase.eventDao().deleteEvent(event)).start();
    }

    public LiveData<Event> getExit() {
        return eventsDatabase.eventDao().getEvent(EVENTS.EXIT);
    }

    public LiveData<Event> getPermission() {
        return eventsDatabase.eventDao().getEvent(EVENTS.PERMISSION);
    }
}