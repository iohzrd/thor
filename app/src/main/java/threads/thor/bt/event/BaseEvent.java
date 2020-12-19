package threads.thor.bt.event;

import androidx.annotation.NonNull;

public abstract class BaseEvent implements Comparable<BaseEvent> {

    private final long id;
    private final Object objectId;
    private final long timestamp;

    /**
     * @param id        Unique event ID
     * @param timestamp Timestamp
     * @since 1.5
     */
    BaseEvent(long id, long timestamp) {
        if (id <= 0 || timestamp <= 0) {
            throw new IllegalArgumentException("Invalid arguments: id (" + id + "), timestamp (" + timestamp + ")");
        }
        this.id = id;
        this.objectId = id;
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(BaseEvent that) {
        return (int) (this.id - that.id);
    }

    @Override
    public int hashCode() {
        return objectId.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof BaseEvent)) {
            return false;
        }
        BaseEvent that = (BaseEvent) o;
        return this.id == that.id;
    }

    @Override
    @NonNull
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "] id {" + id + "}, timestamp {" + timestamp + "}";
    }
}
