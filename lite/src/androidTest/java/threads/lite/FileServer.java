package threads.lite;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static androidx.core.util.Preconditions.checkNotNull;
import static androidx.test.internal.util.Checks.checkArgument;

class FileServer {


    static void insertRecord(@NonNull File file,
                             @NonNull Integer index,
                             @NonNull Integer packetSize,
                             @NonNull byte[] bytes) throws Exception {

        checkNotNull(index);
        checkNotNull(packetSize);
        checkNotNull(bytes);

        checkArgument(packetSize > 0);

        ByteBuffer data = ByteBuffer.allocate(packetSize);
        data.clear();
        checkArgument(file.exists());
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        FileChannel channel = randomAccessFile.getChannel();
        checkArgument(channel.isOpen());
        long pos = index * packetSize;
        channel.position(pos);
        checkArgument(pos == channel.position());

        try {
            data.put(bytes);

            data.flip();

            while (data.hasRemaining()) {
                channel.write(data);
            }
        } finally {
            data.clear();
            channel.close();
            randomAccessFile.close();
        }
    }


}
