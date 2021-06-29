package threads.lite.utils;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import threads.lite.core.ClosedException;
import threads.lite.core.Progress;

public class ProgressStream extends InputStream {
    private static final String TAG = ProgressStream.class.getSimpleName();
    private final Reader mReader;
    private final Progress mProgress;
    private final long size;
    private int position = 0;
    private byte[] data = null;
    private int remember = 0;
    private long totalRead = 0L;

    public ProgressStream(@NonNull Reader reader, @NonNull Progress progress) {
        mReader = reader;
        mProgress = progress;
        size = reader.getSize();
    }

    @Override
    public int available() {
        long size = mReader.getSize();
        return (int) size;
    }


    @Override
    public int read() throws IOException {


        try {
            if (data == null) {
                invalidate();
                preLoad();
            }
            if (data == null) {
                return -1;
            }
            if (position < data.length) {
                byte value = data[position];
                position++;
                return (value & 0xff);
            } else {
                invalidate();
                if (preLoad()) {
                    byte value = data[position];
                    position++;
                    return (value & 0xff);
                } else {
                    return -1;
                }
            }

        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    private void invalidate() {
        position = 0;
        data = null;
    }


    private boolean preLoad() throws ClosedException {

        data = mReader.loadNextData();
        if (data != null) {
            int read = data.length;
            totalRead += read;
            if (mProgress.doProgress()) {
                if (size > 0) {
                    int percent = (int) ((totalRead * 100.0f) / size);
                    if (remember < percent) {
                        remember = percent;
                        mProgress.setProgress(percent);
                    }
                }
            }
        }
        return data != null;
    }

}
