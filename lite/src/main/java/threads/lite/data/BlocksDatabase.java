package threads.lite.data;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Block.class}, version = 1, exportSchema = false)
public abstract class BlocksDatabase extends RoomDatabase {

    public abstract BlockDao blockDao();

}
