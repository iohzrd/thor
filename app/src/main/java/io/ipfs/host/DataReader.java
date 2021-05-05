package io.ipfs.host;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.ipfs.IPFS;
import io.ipfs.core.ProtocolIssue;
import io.ipfs.multibase.Charsets;
import io.ipfs.multihash.Multihash;

public class DataReader {
    private final List<String> tokens = new ArrayList<>();
    private final int maxLength;
    private boolean isDone = false;
    private byte[] message = null;
    private int expectedLength;

    public DataReader(int maxLength) {
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

    public byte[] getMessage() {
        return message;
    }

    public boolean isDone() {
        return isDone;
    }

    public void setDone(boolean done) {
        isDone = done;
    }

    public List<String> getTokens() {
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
        }

        // shorcut
        if (data.length < expectedLength) {
            // no reading required
            return;
        }


        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            expectedLength = (int) Multihash.readVarint(inputStream);

            if (expectedLength > maxLength) {
                throw new ProtocolIssue();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int read = copy(inputStream, outputStream, expectedLength);
            if (read == expectedLength) {
                // token found
                byte[] tokenData = outputStream.toByteArray();
                if (tokenData[read - 1] == '\n') { // expected to be for a token

                    // TODO compare with expected tokens
                    try {
                        String token = new String(tokenData, Charsets.UTF_8);
                        token = token.substring(0, read - 1);
                        tokens.add(token);
                    } catch (Throwable throwable) {
                        message = tokenData;
                    }
                } else {
                    message = tokenData;
                }
                // check if still data to read
                ByteArrayOutputStream rest = new ByteArrayOutputStream();
                int restRead = copy(inputStream, rest);
                if (restRead == 0) {
                    isDone = true;
                } else {
                    DataReader sub = new DataReader(maxLength);
                    sub.load(rest.toByteArray());
                    this.merge(sub);
                }
            }
        }

    }

    private void merge(DataReader dataReader) {
        this.expectedLength = dataReader.expectedLength;
        this.isDone = dataReader.isDone;
        this.tokens.addAll(dataReader.tokens);
        if (this.message == null) {
            this.message = dataReader.message;
        } else {
            throw new RuntimeException("illegal state");
        }
    }

    public int expectedBytes() {
        return expectedLength;

    }
}
