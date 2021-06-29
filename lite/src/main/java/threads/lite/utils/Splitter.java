package threads.lite.utils;

import threads.lite.format.Reader;

public interface Splitter {
    Reader Reader();

    byte[] NextBytes();

    boolean Done();
}
