package threads.lite.format;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.List;

public interface Resolver {

    Pair<Object, List<String>> resolve(@NonNull List<String> path);

}
