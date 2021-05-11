package io.ipfs.host;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.LogUtils;
import io.core.ProtocolIssue;
import io.ipfs.IPFS;
import io.ipfs.multibase.Charsets;
import io.ipfs.multihash.Multihash;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DataHandler {
    private static final String TAG = DataHandler.class.getSimpleName();
    private final Set<String> tokens = new HashSet<>();
    private final int maxLength;
    private ByteArrayOutputStream temp = new ByteArrayOutputStream();
    private boolean isDone = false;
    private byte[] message = null;
    private int expectedLength;

    public DataHandler(int maxLength) {
        this.expectedLength = 0;
        this.maxLength = maxLength;
    }

    private static int copy(InputStream source, OutputStream sink, int length) throws IOException {
        int nread = 0;
        byte[] buf = new byte[length];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
            if (nread == length) {
                break;
            }
        }
        return nread;
    }

    private static int copy(InputStream source, OutputStream sink) throws IOException {
        int nread = 0;
        byte[] buf = new byte[4096];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }

    public static ByteBuf encode(@NonNull MessageLite message) {
        byte[] data = message.toByteArray();
        return encode(data);
    }

    public static ByteBuf encode(@NonNull byte[] data) {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            Multihash.putUvarint(buf, data.length);
            buf.write(data);
            return Unpooled.buffer().writeBytes(buf.toByteArray());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static ByteBuf writeToken(String token) {
        byte[] data = token.getBytes(Charsets.UTF_8);
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            Multihash.putUvarint(buf, data.length + 1);
            buf.write(token.getBytes(Charsets.UTF_8));
            buf.write('\n');
            return Unpooled.buffer().writeBytes(buf.toByteArray());
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public int hasRead() {
        return temp.size();
    }

    public byte[] getMessage() {
        return message;
    }

    public boolean isDone() {
        return isDone;
    }

    public void setDone(boolean done) {
        isDone = done;
    }

    public Set<String> getTokens() {
        return tokens;
    }

    public void load(@NonNull byte[] data)
            throws IOException, ProtocolIssue {


        // shortcut NA
        if (expectedLength == 0) {
            if (Arrays.equals(data, IPFS.NA.getBytes())) {
                tokens.add(IPFS.NA);
                isDone = true;
                return;
            }
            if (Arrays.equals(data, IPFS.LS.getBytes())) {
                tokens.add(IPFS.LS);
                isDone = true;
                return;
            }
        }
        temp.write(data);


        // shorcut
        if (temp.size() < expectedLength) {
            // no reading required
            return;
        }


        try (InputStream inputStream = new ByteArrayInputStream(temp.toByteArray())) {
            expectedLength = (int) Multihash.readVarint(inputStream);

            //LogUtils.error(TAG, "" + expectedLength);
            if (expectedLength > maxLength) {
                LogUtils.error(TAG, "expected length " + expectedLength + " max length " + maxLength);
                throw new ProtocolIssue();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int read = copy(inputStream, outputStream, expectedLength);
            if (read == expectedLength) {
                // token found
                byte[] tokenData = outputStream.toByteArray();
                // expected to be for a token
                if (tokenData[0] == '/' && tokenData[read - 1] == '\n') {

                    String token = new String(tokenData, Charsets.UTF_8);
                    token = token.substring(0, read - 1);
                    tokens.add(token);
                    //LogUtils.error(TAG, "token  " + token);
                } else {
                    //LogUtils.error(TAG, "token ??? " + new String(tokenData));
                    message = tokenData;
                }
                // check if still data to read
                ByteArrayOutputStream rest = new ByteArrayOutputStream();
                int restRead = copy(inputStream, rest);
                if (restRead == 0) {
                    isDone = true;
                } else {
                    DataHandler sub = new DataHandler(maxLength);
                    sub.load(rest.toByteArray());
                    this.merge(sub);
                }
            }
        }

    }

    private void merge(DataHandler dataReader) throws IOException {
        this.expectedLength = dataReader.expectedLength;
        this.isDone = dataReader.isDone;
        this.tokens.addAll(dataReader.tokens);
        this.message = dataReader.message;
        this.temp.close();
        this.temp = dataReader.temp;
    }

    public int expectedBytes() {
        return expectedLength;

    }
}
