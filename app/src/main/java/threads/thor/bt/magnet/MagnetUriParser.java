
package threads.thor.bt.magnet;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import threads.LogUtils;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.net.InetPeerAddress;
import threads.thor.bt.protocol.Protocols;


public class MagnetUriParser {

    private static final String TAG = MagnetUriParser.class.getSimpleName();
    private static final String SCHEME = "magnet";
    private static final String INFOHASH_PREFIX = "urn:btih:";
    private static final String MULTIHASH_PREFIX = "urn:btmh:";
    private final boolean lenient;

    private MagnetUriParser(boolean lenient) {
        this.lenient = lenient;
    }

    /**
     * Creates a parser, that will throw an exception,
     * when some of the elements of the magnet link can not be parsed,
     * e.g. invalid peer addresses.
     *
     * @return Magnet URI parser
     * @since 1.3
     */
    public static MagnetUriParser parser() {
        return new MagnetUriParser(false);
    }

    /**
     * Creates a parser, that will suppress parsing errors,
     * that do not prevent identification of threads.torrent ID,
     * e.g. invalid peer addresses.
     *
     * @return Magnet URI parser
     * @since 1.3
     */
    public static MagnetUriParser lenientParser() {
        return new MagnetUriParser(true);
    }

    /**
     * Create a magnet URI from its' string representation in BEP-9 format.
     * Current limitations:
     * - only v1 links are supported (xt=urn:btih:&lt;info-hash&gt;)
     * - base32-encoded info hashes are not supported
     *
     * @since 1.3
     */
    public MagnetUri parse(String uriString) {
        try {
            return parse(new URI(uriString));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + uriString, e);
        }
    }

    /**
     * Create a magnet URI from its' URI representation in BEP-9 format.
     * Current limitations:
     * - only v1 links are supported (xt=urn:btih:&lt;info-hash&gt;)
     * - base32-encoded info hashes are not supported
     *
     * @since 1.3
     */
    private MagnetUri parse(URI uri) {
        if (!SCHEME.equals(uri.getScheme())) {
            throw new IllegalArgumentException("Invalid scheme: " + uri.getScheme());
        }

        Map<String, List<String>> paramsMap = collectParams(uri);

        Set<String> infoHashes = getRequiredParam(paramsMap).stream()
                .filter(value -> value.startsWith(INFOHASH_PREFIX))
                .map(value -> value.substring(INFOHASH_PREFIX.length()))
                .collect(Collectors.toSet());
        if (infoHashes.size() != 1) {
            throw new IllegalStateException(String.format("Parameter '%s' has invalid number of values with prefix '%s': %s",
                    UriParams.TORRENT_ID, INFOHASH_PREFIX, infoHashes.size()));
        }
        TorrentId torrentId = buildTorrentId(infoHashes.iterator().next());
        MagnetUri.Builder builder = MagnetUri.torrentId(torrentId);

        getOptionalParam(UriParams.DISPLAY_NAME, paramsMap).stream().findAny().ifPresent(builder::name);
        getOptionalParam(UriParams.TRACKER_URL, paramsMap).forEach(builder::tracker);
        getOptionalParam(UriParams.PEER, paramsMap).forEach(value -> {
            try {
                builder.peer(parsePeer(value));
            } catch (Exception e) {
                if (lenient) {
                    LogUtils.warning(TAG, "Failed to parse peer address: " + value);
                } else {
                    throw new RuntimeException("Failed to parse peer address: " + value, e);
                }
            }
        });

        return builder.buildUri();
    }

    private Map<String, List<String>> collectParams(URI uri) {
        Map<String, List<String>> paramsMap = new HashMap<>();

        // magnet:?param1=value1...
        // uri.getSchemeSpecificPart() will start with the question mark and contain all name-value pairs
        String[] params = uri.getSchemeSpecificPart().substring(1).split("&");
        for (String param : params) {
            String[] parts = param.split("=");
            String name = parts[0];
            String value = parts[1];
            List<String> values = paramsMap.get(name);
            if (values == null) {
                values = new ArrayList<>();
                paramsMap.put(name, values);
            }
            values.add(value);
        }

        return paramsMap;
    }

    private List<String> getRequiredParam(Map<String, List<String>> paramsMap) {
        List<String> values = paramsMap.getOrDefault(UriParams.TORRENT_ID, Collections.emptyList());
        Objects.requireNonNull(values);
        if (values.isEmpty()) {
            throw new IllegalStateException(String.format("Required parameter '%s' is missing: %s",
                    UriParams.TORRENT_ID, values.size()));
        }
        return values;
    }

    private List<String> getOptionalParam(String paramName,
                                          Map<String, List<String>> paramsMap) {
        return paramsMap.getOrDefault(paramName, Collections.emptyList());
    }

    private TorrentId buildTorrentId(String infoHash) {
        byte[] bytes;
        int len = infoHash.length();
        if (len == 40) {
            bytes = Protocols.fromHex(infoHash);
        } else if (len == 32) {
            bytes = Protocols.infoHashFromBase32(infoHash);
        } else {
            throw new IllegalArgumentException("Invalid info hash length: " + len);
        }
        return TorrentId.fromBytes(bytes);
    }

    private InetPeerAddress parsePeer(String value) {
        String[] parts = value.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid peer format: " + value + "; should be <host>:<port>");
        }
        String hostname = parts[0];
        int port = Integer.parseInt(parts[1]);
        return new InetPeerAddress(hostname, port);
    }

    private static class UriParams {
        private static final String TORRENT_ID = "xt";
        private static final String DISPLAY_NAME = "dn";
        private static final String TRACKER_URL = "tr";
        private static final String PEER = "x.pe";
    }
}
