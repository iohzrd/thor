package threads.thor.bt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import threads.thor.bt.data.digest.Digester;
import threads.thor.bt.data.digest.JavaSecurityDigester;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentFile;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.metainfo.TorrentSource;
import threads.thor.bt.tracker.AnnounceKey;

public class TorrentSerializer {

    public final static String URL_LIST = "url-list";
    public static final int DEFAULT_ANNOUNCE_INTERVAL_SEC = 15;
    public final static int DEFAULT_SOCKET_CONNECTION_TIMEOUT_MILLIS = 100000;
    public static final int DEFAULT_CONNECTION_TIMEOUT_MILLIS = 10000;
    public static final int DEFAULT_MAX_CONNECTION_COUNT = 100;
    public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    public static final int DEFAULT_SELECTOR_SELECT_TIMEOUT_MILLIS = 10000;
    public static final int DEFAULT_CLEANUP_RUN_TIMEOUT_MILLIS = 120000;
    public static final String BYTE_ENCODING = "ISO-8859-1";
    public static final int PIECE_HASH_SIZE = 20;
    private final static String MD5_SUM = "md5sum";
    private final static String FILE_LENGTH = "length";
    private final static String FILES = "files";
    private final static String FILE_PATH = "path";
    private final static String FILE_PATH_UTF8 = "path.utf-8";
    private final static String COMMENT = "comment";
    private final static String CREATED_BY = "created by";
    private final static String ANNOUNCE = "announce";
    private final static String PIECE_LENGTH = "piece length";
    private final static String PIECES = "pieces";
    private final static String CREATION_DATE_SEC = "creation date";
    private final static String PRIVATE = "private";
    private final static String NAME = "name";
    private final static String INFO_TABLE = "info";
    private final static String ANNOUNCE_LIST = "announce-list";


    private static Digester provideDigester() {
        int step = 2 << 22; // 8 MB
        return new JavaSecurityDigester("SHA-1", step);
    }

    /**
     * @param data for hashing
     * @return sha 1 hash of specified data
     */
    private static byte[] calculateSha1Hash(byte[] data) {
        return provideDigester().digest(data);
    }

    /**
     * @param metadata binary .torrent content
     * @return parsed metadata object. This parser also wraps single torrent as multi torrent with one file
     * @throws InvalidBEncodingException if metadata has incorrect BEP format or missing required fields
     * @throws RuntimeException          It's wrapped io exception from bep decoder.
     *                                   This exception doesn't must to throw io exception because reading from
     *                                   byte array input stream cannot throw the exception
     */
    public static Torrent parse(byte[] metadata) throws InvalidBEncodingException, RuntimeException {
        final Map<String, BEValue> dictionaryMetadata;
        try {
            dictionaryMetadata = BDecoder.bdecode(new ByteArrayInputStream(metadata)).getMap();
        } catch (InvalidBEncodingException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Map<String, BEValue> infoTable = getRequiredValueOrThrowException(dictionaryMetadata, INFO_TABLE).getMap();

        final BEValue creationDateValue = dictionaryMetadata.get(CREATION_DATE_SEC);
        final long creationDate = creationDateValue == null ? -1 : creationDateValue.getLong();

        final String comment = getStringOrNull(dictionaryMetadata, COMMENT);
        final String createdBy = getStringOrNull(dictionaryMetadata, CREATED_BY);
        final String announceUrl = getStringOrNull(dictionaryMetadata, ANNOUNCE);
        final List<List<String>> trackers = getTrackers(dictionaryMetadata);
        final int pieceLength = getRequiredValueOrThrowException(infoTable, PIECE_LENGTH).getInt();
        final byte[] piecesHashes = getRequiredValueOrThrowException(infoTable, PIECES).getBytes();

        final boolean torrentContainsManyFiles = infoTable.get(FILES) != null;

        final String name = getRequiredValueOrThrowException(infoTable, NAME).getString();

        final List<TorrentFile> files = parseFiles(infoTable, torrentContainsManyFiles, name);

        long size = 0L;
        for (TorrentFile file : files) {
            size = size + file.getSize();
        }

        byte[] infoTableBytes;
        try {
            infoTableBytes = BEncoder.bencode(infoTable).array();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        byte[] torrentData = calculateSha1Hash(infoTableBytes);
        TorrentId torrentId = TorrentId.fromBytes(torrentData);

        Torrent torrent = Torrent.createTorrent(torrentId,
                name, new TorrentSource() {
                    @Override
                    public Optional<byte[]> getMetadata() {
                        return Optional.empty();
                    }

                    @Override
                    public byte[] getExchangedMetadata() {
                        return metadata;
                    }
                }, files, piecesHashes, size, pieceLength);

        torrent.setCreatedBy(createdBy);
        torrent.setCreationDate(creationDate);
        torrent.setPrivate(false);
        torrent.setComment(comment);

        if (trackers != null) {
            torrent.setAnnounceKey(new AnnounceKey(announceUrl, trackers));
        } else {
            if (announceUrl != null) {
                torrent.setAnnounceKey(new AnnounceKey(announceUrl));
            }
        }
        return torrent;

    }

    private static List<TorrentFile> parseFiles(Map<String, BEValue> infoTable, boolean torrentContainsManyFiles, String name) throws InvalidBEncodingException {
        /*
        if (!torrentContainsManyFiles) {
            final BEValue md5Sum = infoTable.get(MD5_SUM);
            return Collections.singletonList(new TorrentFile(
                    Collections.singletonList(name),
                    getRequiredValueOrThrowException(infoTable, FILE_LENGTH).getLong(),
                    md5Sum == null ? null : md5Sum.getString()
            ));
        }*/

        List<TorrentFile> result = new ArrayList<>();
        for (BEValue file : infoTable.get(FILES).getList()) {
            Map<String, BEValue> fileInfo = file.getMap();
            List<String> path = new ArrayList<>();
            BEValue filePathList = fileInfo.get(FILE_PATH_UTF8);
            if (filePathList == null) {
                filePathList = fileInfo.get(FILE_PATH);
            }
            for (BEValue pathElement : filePathList.getList()) {
                path.add(pathElement.getString());
            }
            final BEValue md5Sum = infoTable.get(MD5_SUM);
            TorrentFile torrentFile = new TorrentFile(
                    fileInfo.get(FILE_LENGTH).getLong());
            torrentFile.setPathElements(path);

            /*
            result.add(new TorrentFile(
                    path,
                    fileInfo.get(FILE_LENGTH).getLong(),
                    md5Sum == null ? null : md5Sum.getString()));*/
            result.add(torrentFile);
        }
        return result;
    }

    @Nullable
    private static String getStringOrNull(Map<String, BEValue> dictionaryMetadata, String key) throws InvalidBEncodingException {
        final BEValue value = dictionaryMetadata.get(key);
        if (value == null) return null;
        return value.getString();
    }

    @Nullable
    private static List<List<String>> getTrackers(Map<String, BEValue> dictionaryMetadata) throws InvalidBEncodingException {
        final BEValue announceListValue = dictionaryMetadata.get(ANNOUNCE_LIST);
        if (announceListValue == null) return null;
        List<BEValue> announceList = announceListValue.getList();
        List<List<String>> result = new ArrayList<>();
        Set<String> allTrackers = new HashSet<>();
        for (BEValue tv : announceList) {
            List<BEValue> trackers = tv.getList();
            if (trackers.isEmpty()) {
                continue;
            }

            List<String> tier = new ArrayList<>();
            for (BEValue tracker : trackers) {
                final String url = tracker.getString();
                if (!allTrackers.contains(url)) {
                    tier.add(url);
                    allTrackers.add(url);
                }
            }

            if (!tier.isEmpty()) {
                result.add(tier);
            }
        }
        return result;
    }

    @NonNull
    private static BEValue getRequiredValueOrThrowException(Map<String, BEValue> map, String key) throws InvalidBEncodingException {
        final BEValue value = map.get(key);
        if (value == null)
            throw new InvalidBEncodingException("Invalid metadata format. Map doesn't contain required field " + key);
        return value;
    }


    public static byte[] serialize(Torrent metadata) throws IOException {
        Map<String, BEValue> mapMetadata = new HashMap<>();
        Map<String, BEValue> infoTable = new HashMap<>();


        AnnounceKey announce = metadata.getAnnounceKey();

        if (announce != null) {
            mapMetadata.put(ANNOUNCE, new BEValue(announce.getTrackerUrl()));
        }

        putOptionalIfPresent(mapMetadata, COMMENT, metadata.getComment());
        putOptionalIfPresent(mapMetadata, CREATED_BY, metadata.getCreatedBy());

        if (metadata.getCreationDate() > 0) {
            mapMetadata.put(CREATION_DATE_SEC, new BEValue(metadata.getCreationDate()));
        }
        if (announce != null) {
            List<BEValue> announceList = getAnnounceListAsBEValues(announce.getTrackerUrls());
            if (announceList != null) {
                mapMetadata.put(ANNOUNCE_LIST, new BEValue(announceList));
            }
        }

        infoTable.put(PIECE_LENGTH, new BEValue(metadata.getChunkSize()));
        infoTable.put(PIECES, new BEValue(metadata.getChunkHashes()));
        if (metadata.isPrivate()) {
            infoTable.put(PRIVATE, new BEValue(1));
        }

        infoTable.put(NAME, new BEValue(metadata.getName()));
        if (metadata.getFiles().size() == 1) {
            TorrentFile torrentFile = metadata.getFiles().get(0);
            infoTable.put(FILE_LENGTH, new BEValue(torrentFile.getSize()));
            //putOptionalIfPresent(infoTable, MD5_SUM, torrentFile.md5Hash);
        } else {
            List<BEValue> files = new ArrayList<>();
            for (TorrentFile torrentFile : metadata.getFiles()) {
                Map<String, BEValue> entry = new HashMap<>();
                entry.put(FILE_LENGTH, new BEValue(torrentFile.getSize()));
                //putOptionalIfPresent(entry, MD5_SUM, torrentFile.md5Hash);
                entry.put(FILE_PATH, new BEValue(mapStringListToBEValueList(torrentFile.getPathElements())));
                files.add(new BEValue(entry));
            }
            infoTable.put(FILES, new BEValue(files));
        }

        mapMetadata.put(INFO_TABLE, new BEValue(infoTable));

        final ByteBuffer buffer = BEncoder.bencode(mapMetadata);
        return buffer.array();
    }

    @Nullable
    private static List<BEValue> getAnnounceListAsBEValues(@Nullable List<List<String>> announceList) {
        if (announceList == null) return null;
        List<BEValue> result = new ArrayList<>();

        for (List<String> announceTier : announceList) {
            List<BEValue> tier = mapStringListToBEValueList(announceTier);
            if (!tier.isEmpty()) result.add(new BEValue(tier));
        }

        if (result.isEmpty()) return null;

        return result;
    }

    private static List<BEValue> mapStringListToBEValueList(List<String> list) {
        List<BEValue> result = new ArrayList<>();
        for (String s : list) {
            result.add(new BEValue(s));
        }
        return result;
    }


    private static void putOptionalIfPresent(Map<String, BEValue> map,
                                             @NonNull String key, @Nullable String optional) {
        if (optional == null) return;
        map.put(key, new BEValue(optional));
    }

    /**
     * B-encoding encoder.
     *
     * <p>
     * This class provides utility methods to encode objects and
     * {@link BEValue}s to B-encoding into a provided output stream.
     * </p>
     *
     * <p>
     * Inspired by Snark's implementation.
     * </p>
     *
     * @author mpetazzoni
     * @see <a href="http://en.wikipedia.org/wiki/Bencode">B-encoding specification</a>
     */
    static class BEncoder {

        @SuppressWarnings("unchecked")
        static void bencode(Object o, OutputStream out)
                throws IOException, IllegalArgumentException {
            if (o instanceof BEValue) {
                o = ((BEValue) o).getValue();
            }

            if (o instanceof String) {
                bencode((String) o, out);
            } else if (o instanceof byte[]) {
                bencode((byte[]) o, out);
            } else if (o instanceof Number) {
                bencode((Number) o, out);
            } else if (o instanceof List) {
                bencode((List<BEValue>) o, out);
            } else if (o instanceof Map) {
                bencode((Map<String, BEValue>) o, out);
            } else {
                throw new IllegalArgumentException("Cannot bencode: " +
                        o.getClass());
            }
        }

        static void bencode(String s, OutputStream out) throws IOException {
            byte[] bs = s.getBytes(StandardCharsets.UTF_8);
            bencode(bs, out);
        }

        static void bencode(Number n, OutputStream out) throws IOException {
            out.write('i');
            String s = n.toString();
            out.write(s.getBytes(StandardCharsets.UTF_8));
            out.write('e');
        }

        static void bencode(List<BEValue> l, OutputStream out)
                throws IOException {
            out.write('l');
            for (BEValue value : l) {
                bencode(value, out);
            }
            out.write('e');
        }

        static void bencode(byte[] bs, OutputStream out) throws IOException {
            String l = Integer.toString(bs.length);
            out.write(l.getBytes(StandardCharsets.UTF_8));
            out.write(':');
            out.write(bs);
        }

        static void bencode(Map<String, BEValue> m, OutputStream out)
                throws IOException {
            out.write('d');

            // Keys must be sorted.
            Set<String> s = m.keySet();
            List<String> l = new ArrayList<>(s);
            Collections.sort(l);

            for (String key : l) {
                Object value = m.get(key);
                bencode(key, out);
                bencode(value, out);
            }

            out.write('e');
        }

        static ByteBuffer bencode(Map<String, BEValue> m)
                throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BEncoder.bencode(m, baos);
            baos.close();
            return ByteBuffer.wrap(baos.toByteArray());
        }
    }

    public static class BEValue {

        /**
         * The B-encoded value can be a byte array, a Number, a List or a Map.
         * Lists and Maps contains BEValues too.
         */
        private final Object value;

        BEValue(byte[] value) {
            this.value = value;
        }

        BEValue(String value) {
            this.value = value.getBytes(StandardCharsets.UTF_8);
        }

        public BEValue(String value, String enc)
                throws UnsupportedEncodingException {
            this.value = value.getBytes(enc);
        }

        BEValue(int value) {
            this.value = value;
        }

        BEValue(long value) {
            this.value = value;
        }

        BEValue(Number value) {
            this.value = value;
        }

        BEValue(List<BEValue> value) {
            this.value = value;
        }

        BEValue(Map<String, BEValue> value) {
            this.value = value;
        }

        Object getValue() {
            return this.value;
        }

        /**
         * Returns this BEValue as a String, interpreted as UTF-8.
         *
         * @throws InvalidBEncodingException If the value is not a byte[].
         */
        String getString() throws InvalidBEncodingException {
            return this.getString("UTF-8");
        }

        /**
         * Returns this BEValue as a String, interpreted with the specified
         * encoding.
         *
         * @param encoding The encoding to interpret the bytes as when converting
         *                 them into a {@link String}.
         * @throws InvalidBEncodingException If the value is not a byte[].
         */
        String getString(String encoding) throws InvalidBEncodingException {
            try {
                return new String(this.getBytes(), encoding);
            } catch (ClassCastException cce) {
                throw new InvalidBEncodingException(cce.toString());
            } catch (UnsupportedEncodingException uee) {
                throw new InternalError(uee.toString());
            }
        }

        /**
         * Returns this BEValue as a byte[].
         *
         * @throws InvalidBEncodingException If the value is not a byte[].
         */
        byte[] getBytes() throws InvalidBEncodingException {
            try {
                return (byte[]) this.value;
            } catch (ClassCastException cce) {
                throw new InvalidBEncodingException(cce.toString());
            }
        }

        /**
         * Returns this BEValue as a Number.
         *
         * @throws InvalidBEncodingException If the value is not a {@link Number}.
         */
        Number getNumber() throws InvalidBEncodingException {
            try {
                return (Number) this.value;
            } catch (ClassCastException cce) {
                throw new InvalidBEncodingException(cce.toString());
            }
        }

        /**
         * Returns this BEValue as short.
         *
         * @throws InvalidBEncodingException If the value is not a {@link Number}.
         */
        public short getShort() throws InvalidBEncodingException {
            return this.getNumber().shortValue();
        }

        /**
         * Returns this BEValue as int.
         *
         * @throws InvalidBEncodingException If the value is not a {@link Number}.
         */
        int getInt() throws InvalidBEncodingException {
            return this.getNumber().intValue();
        }

        /**
         * Returns this BEValue as long.
         *
         * @throws InvalidBEncodingException If the value is not a {@link Number}.
         */
        long getLong() throws InvalidBEncodingException {
            return this.getNumber().longValue();
        }

        /**
         * Returns this BEValue as a List of BEValues.
         *
         * @throws InvalidBEncodingException If the value is not an
         *                                   {@link ArrayList}.
         */
        @SuppressWarnings("unchecked")
        List<BEValue> getList() throws InvalidBEncodingException {
            if (this.value instanceof ArrayList) {
                return (ArrayList<BEValue>) this.value;
            } else {
                throw new InvalidBEncodingException("Excepted List<BEvalue> !");
            }
        }

        /**
         * Returns this BEValue as a Map of String keys and BEValue values.
         *
         * @throws InvalidBEncodingException If the value is not a {@link HashMap}.
         */
        @SuppressWarnings("unchecked")
        Map<String, BEValue> getMap() throws InvalidBEncodingException {
            if (this.value instanceof HashMap) {
                return (Map<String, BEValue>) this.value;
            } else {
                throw new InvalidBEncodingException("Expected Map<String, BEValue> !");
            }
        }
    }

    public static class InvalidBEncodingException extends IOException {

        public static final long serialVersionUID = -1;

        InvalidBEncodingException(String message) {
            super(message);
        }
    }


    /**
     * B-encoding decoder.
     *
     * <p>
     * A b-encoded byte stream can represent byte arrays, numbers, lists and maps
     * (dictionaries). This class implements a decoder of such streams into
     * {@link BEValue}s.
     * </p>
     *
     * <p>
     * Inspired by Snark's implementation.
     * </p>
     *
     * @author mpetazzoni
     * @see <a href="http://en.wikipedia.org/wiki/Bencode">B-encoding specification</a>
     */
    static class BDecoder {

        // The InputStream to BDecode.
        private final InputStream in;

        // The last indicator read.
        // Zero if unknown.
        // '0'..'9' indicates a byte[].
        // 'i' indicates an Number.
        // 'l' indicates a List.
        // 'd' indicates a Map.
        // 'e' indicates end of Number, List or Map (only used internally).
        // -1 indicates end of stream.
        // Call getNextIndicator to get the current value (will never return zero).
        private int indicator = 0;

        /**
         * Initializes a new BDecoder.
         *
         * <p>
         * Nothing is read from the given <code>InputStream</code> yet.
         * </p>
         *
         * @param in The input stream to read from.
         */
        BDecoder(InputStream in) {
            this.in = in;
        }

        /**
         * Decode a B-encoded stream.
         *
         * <p>
         * Automatically instantiates a new BDecoder for the provided input stream
         * and decodes its root member.
         * </p>
         *
         * @param in The input stream to read from.
         */
        static BEValue bdecode(InputStream in) throws IOException {
            return new BDecoder(in).bdecode();
        }

        /**
         * Decode a B-encoded byte buffer.
         *
         * <p>
         * Automatically instantiates a new BDecoder for the provided buffer and
         * decodes its root member.
         * </p>
         *
         * @param data The {@link ByteBuffer} to read from.
         */
        public static BEValue bdecode(ByteBuffer data) throws IOException {
            return BDecoder.bdecode(new ByteArrayInputStream(data.array()));
        }

        /**
         * Returns what the next b-encoded object will be on the stream or -1
         * when the end of stream has been reached.
         *
         * <p>
         * Can return something unexpected (not '0' .. '9', 'i', 'l' or 'd') when
         * the stream isn't b-encoded.
         * </p>
         * <p>
         * This might or might not read one extra byte from the stream.
         */
        private int getNextIndicator() throws IOException {
            if (this.indicator == 0) {
                this.indicator = in.read();
            }
            return this.indicator;
        }

        /**
         * Gets the next indicator and returns either null when the stream
         * has ended or b-decodes the rest of the stream and returns the
         * appropriate BEValue encoded object.
         */
        BEValue bdecode() throws IOException {
            if (this.getNextIndicator() == -1)
                return null;

            if (this.indicator >= '0' && this.indicator <= '9')
                return this.bdecodeBytes();
            else if (this.indicator == 'i')
                return this.bdecodeNumber();
            else if (this.indicator == 'l')
                return this.bdecodeList();
            else if (this.indicator == 'd')
                return this.bdecodeMap();
            else
                throw new InvalidBEncodingException
                        ("Unknown indicator '" + this.indicator + "'");
        }

        /**
         * Returns the next b-encoded value on the stream and makes sure it is a
         * byte array.
         *
         * @throws InvalidBEncodingException If it is not a b-encoded byte array.
         */
        BEValue bdecodeBytes() throws IOException {
            int c = this.getNextIndicator();
            int num = c - '0';
            if (num < 0 || num > 9)
                throw new InvalidBEncodingException("Number expected, not '"
                        + (char) c + "'");
            this.indicator = 0;

            c = this.read();
            int i = c - '0';
            while (i >= 0 && i <= 9) {
                // This can overflow!
                num = num * 10 + i;
                c = this.read();
                i = c - '0';
            }

            if (c != ':') {
                throw new InvalidBEncodingException("Colon expected, not '" +
                        (char) c + "'");
            }

            return new BEValue(read(num));
        }

        /**
         * Returns the next b-encoded value on the stream and makes sure it is a
         * number.
         *
         * @throws InvalidBEncodingException If it is not a number.
         */
        BEValue bdecodeNumber() throws IOException {
            int c = this.getNextIndicator();
            if (c != 'i') {
                throw new InvalidBEncodingException("Expected 'i', not '" +
                        (char) c + "'");
            }
            this.indicator = 0;

            c = this.read();
            if (c == '0') {
                c = this.read();
                if (c == 'e')
                    return new BEValue(BigInteger.ZERO);
                else
                    throw new InvalidBEncodingException("'e' expected after zero," +
                            " not '" + (char) c + "'");
            }

            // We don't support more the 255 char big integers
            char[] chars = new char[256];
            int off = 0;

            if (c == '-') {
                c = this.read();
                if (c == '0')
                    throw new InvalidBEncodingException("Negative zero not allowed");
                chars[off] = '-';
                off++;
            }

            if (c < '1' || c > '9')
                throw new InvalidBEncodingException("Invalid Integer start '"
                        + (char) c + "'");
            chars[off] = (char) c;
            off++;

            c = this.read();
            int i = c - '0';
            while (i >= 0 && i <= 9) {
                chars[off] = (char) c;
                off++;
                c = read();
                i = c - '0';
            }

            if (c != 'e')
                throw new InvalidBEncodingException("Integer should end with 'e'");

            String s = new String(chars, 0, off);
            return new BEValue(new BigInteger(s));
        }

        /**
         * Returns the next b-encoded value on the stream and makes sure it is a
         * list.
         *
         * @throws InvalidBEncodingException If it is not a list.
         */
        BEValue bdecodeList() throws IOException {
            int c = this.getNextIndicator();
            if (c != 'l') {
                throw new InvalidBEncodingException("Expected 'l', not '" +
                        (char) c + "'");
            }
            this.indicator = 0;

            List<BEValue> result = new ArrayList<>();
            c = this.getNextIndicator();
            while (c != 'e') {
                result.add(this.bdecode());
                c = this.getNextIndicator();
            }
            this.indicator = 0;

            return new BEValue(result);
        }

        /**
         * Returns the next b-encoded value on the stream and makes sure it is a
         * map (dictionary).
         *
         * @throws InvalidBEncodingException If it is not a map.
         */
        BEValue bdecodeMap() throws IOException {
            int c = this.getNextIndicator();
            if (c != 'd') {
                throw new InvalidBEncodingException("Expected 'd', not '" +
                        (char) c + "'");
            }
            this.indicator = 0;

            Map<String, BEValue> result = new HashMap<>();
            c = this.getNextIndicator();
            while (c != 'e') {
                // Dictionary keys are always strings.
                String key = this.bdecode().getString();

                BEValue value = this.bdecode();
                result.put(key, value);

                c = this.getNextIndicator();
            }
            this.indicator = 0;

            return new BEValue(result);
        }

        /**
         * Returns the next byte read from the InputStream (as int).
         *
         * @throws EOFException If InputStream.read() returned -1.
         */
        private int read() throws IOException {
            int c = this.in.read();
            if (c == -1)
                throw new EOFException();
            return c;
        }

        /**
         * Returns a byte[] containing length valid bytes starting at offset zero.
         *
         * @throws EOFException If InputStream.read() returned -1 before all
         *                      requested bytes could be read.  Note that the byte[] returned might be
         *                      bigger then requested but will only contain length valid bytes.  The
         *                      returned byte[] will be reused when this method is called again.
         */
        private byte[] read(int length) throws IOException {
            byte[] result = new byte[length];

            int read = 0;
            while (read < length) {
                int i = this.in.read(result, read, length - read);
                if (i == -1)
                    throw new EOFException();
                read += i;
            }

            return result;
        }
    }

}
