package io.dht;

public interface ValueStore {

    // SearchValue searches for better and better values from this value
    // store corresponding to the given Key. By default implementations must
    // stop the search after a good value is found. A 'good' value is a value
    // that would be returned from GetValue.
    //
    // Useful when you want a result *now* but still want to hear about
    // better/newer results.
    //
    // Implementations of this methods won't return ErrNotFound. When a value
    // couldn't be found, the channel will get closed without passing any results
    void SearchValue(ResolveInfo resolveInfo, String key, Option... options);
}