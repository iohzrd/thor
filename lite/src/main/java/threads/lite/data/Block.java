package threads.lite.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Block {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private final String id;
    @NonNull
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    private final byte[] data;
    @ColumnInfo(name = "size")
    private final long size;

    Block(@NonNull String id, @NonNull byte[] data, long size) {

        this.id = id;
        this.data = data;
        this.size = size;
    }

    public static Block createBlock(@NonNull String id, @NonNull byte[] data) {
        return new Block(id, data, data.length);
    }

    public long getSize() {
        return size;
    }

    @NonNull
    public byte[] getData() {
        return data;
    }


    @NonNull
    public String getId() {
        return id;
    }


}
