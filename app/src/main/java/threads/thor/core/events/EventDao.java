package threads.thor.core.events;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface EventDao {

    @Query("SELECT * FROM Event WHERE  identifier  =:identifier")
    LiveData<Event> getEvent(String identifier);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEvent(Event event);

    @Delete
    void deleteEvent(Event event);

}
