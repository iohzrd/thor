package threads.thor.core.page;


import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;


@Dao
public interface ResolverDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertResolver(Resolver resolver);

    @Query("SELECT * FROM Resolver WHERE name = :name")
    Resolver getResolver(String name);

    @Query("DELETE FROM Resolver WHERE name = :name")
    void removeResolver(String name);
}
