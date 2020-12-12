package threads.thor.bt.kad;

import androidx.annotation.NonNull;

import java.util.Arrays;

import threads.thor.bt.bencode.Utils;

import static threads.thor.bt.utils.Arrays.compareUnsigned;

/**
 * @author Damokles
 */
public class DBItem implements Comparable<DBItem> {

    private final long time_stamp;
    byte[] item;

    private DBItem() {
        time_stamp = System.currentTimeMillis();
    }

    DBItem(final byte[] ip_port) {
        this();
        item = ip_port.clone();
    }

    boolean expired(final long now) {
        return (now - time_stamp >= DHTConstants.MAX_ITEM_AGE);
    }

    public byte[] getData() {
        return item;
    }

    @NonNull
    @Override
    public String toString() {
        return "DBItem: " + Utils.prettyPrint(item);
    }

    // sort by raw data. only really useful for binary search
    public int compareTo(DBItem other) {
        return compareUnsigned(item, other.item);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof DBItem) {
            byte[] otherItem = ((DBItem) obj).item;
            return Arrays.equals(item, otherItem);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(item);
    }
}
