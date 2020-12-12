package threads.thor.core.page;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {Resolver.class}, version = 1, exportSchema = false)
public abstract class ResolverDatabase extends RoomDatabase {


    public abstract ResolverDao resolverDao();

}
