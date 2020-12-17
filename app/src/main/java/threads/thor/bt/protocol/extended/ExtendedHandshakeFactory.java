package threads.thor.bt.protocol.extended;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.LogUtils;
import threads.thor.bt.Config;
import threads.thor.bt.bencoding.model.BEInteger;
import threads.thor.bt.bencoding.model.BEString;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.protocol.crypto.EncryptionPolicy;
import threads.thor.bt.torrent.TorrentRegistry;

import static threads.thor.bt.protocol.extended.ExtendedHandshake.ENCRYPTION_PROPERTY;
import static threads.thor.bt.protocol.extended.ExtendedHandshake.TCPPORT_PROPERTY;
import static threads.thor.bt.protocol.extended.ExtendedHandshake.VERSION_PROPERTY;

public final class ExtendedHandshakeFactory {
    private static final String TAG = ExtendedHandshakeFactory.class.getSimpleName();
    private static final String UT_METADATA_SIZE_PROPERTY = "metadata_size";

    private static final String VERSION_TEMPLATE = "IL %s";

    private final TorrentRegistry torrentRegistry;
    private final ExtendedMessageTypeMapping messageTypeMapping;
    private final EncryptionPolicy encryptionPolicy;
    private final int tcpAcceptorPort;

    private final ConcurrentMap<TorrentId, ExtendedHandshake> extendedHandshakes;

    public ExtendedHandshakeFactory(TorrentRegistry torrentRegistry,
                                    ExtendedMessageTypeMapping messageTypeMapping,
                                    Config config) {
        this.torrentRegistry = torrentRegistry;
        this.messageTypeMapping = messageTypeMapping;
        this.encryptionPolicy = config.getEncryptionPolicy();
        this.tcpAcceptorPort = config.getAcceptorPort();
        this.extendedHandshakes = new ConcurrentHashMap<>();
    }

    public ExtendedHandshake getHandshake(TorrentId torrentId) {
        ExtendedHandshake handshake = extendedHandshakes.get(torrentId);
        if (handshake == null) {
            handshake = buildHandshake(torrentId);
            ExtendedHandshake existing = extendedHandshakes.putIfAbsent(torrentId, handshake);
            if (existing != null) {
                handshake = existing;
            }
        }
        return handshake;
    }

    private ExtendedHandshake buildHandshake(TorrentId torrentId) {
        ExtendedHandshake.Builder builder = ExtendedHandshake.builder();

        switch (encryptionPolicy) {
            case REQUIRE_PLAINTEXT:
            case PREFER_PLAINTEXT: {
                builder.property(ENCRYPTION_PROPERTY, new BEInteger(null, BigInteger.ZERO));
            }
            case PREFER_ENCRYPTED:
            case REQUIRE_ENCRYPTED: {
                builder.property(ENCRYPTION_PROPERTY, new BEInteger(null, BigInteger.ONE));
            }
            default: {
                // do nothing
            }
        }

        builder.property(TCPPORT_PROPERTY, new BEInteger(null, BigInteger.valueOf(tcpAcceptorPort)));

        try {
            Optional<Torrent> torrentOpt = torrentRegistry.getTorrent(torrentId);
            torrentOpt.ifPresent(torrent -> {
                int metadataSize = torrent.getSource().getExchangedMetadata().length;
                builder.property(UT_METADATA_SIZE_PROPERTY, new BEInteger(null, BigInteger.valueOf(metadataSize)));
            });
        } catch (Exception e) {
            LogUtils.error(TAG, "Failed to get metadata size for threads.torrent ID: " + torrentId, e);
        }

        String version = getVersion();

        builder.property(VERSION_PROPERTY, new BEString(version.getBytes(StandardCharsets.UTF_8)));

        messageTypeMapping.visitMappings(builder::addMessageType);
        return builder.build();
    }

    private String getVersion() {
        return String.format(VERSION_TEMPLATE, "0.5.0");
    }

}
