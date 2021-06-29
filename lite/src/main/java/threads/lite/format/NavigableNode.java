package threads.lite.format;

import androidx.annotation.NonNull;

import threads.lite.cid.Cid;
import threads.lite.core.Closeable;
import threads.lite.core.ClosedException;

public interface NavigableNode {

    NavigableNode fetchChild(@NonNull Closeable ctx, int childIndex) throws ClosedException;

    int childTotal();

    Cid getChild(int index);

    Cid getCid();
}
