/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.protocol.extended;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.LogUtils;
import threads.thor.bt.bencoding.model.BEInteger;
import threads.thor.bt.bencoding.model.BEString;
import threads.thor.bt.metainfo.Torrent;
import threads.thor.bt.metainfo.TorrentId;
import threads.thor.bt.protocol.crypto.EncryptionPolicy;
import threads.thor.bt.runtime.Config;
import threads.thor.bt.torrent.TorrentRegistry;

import static threads.thor.bt.protocol.extended.ExtendedHandshake.ENCRYPTION_PROPERTY;
import static threads.thor.bt.protocol.extended.ExtendedHandshake.TCPPORT_PROPERTY;
import static threads.thor.bt.protocol.extended.ExtendedHandshake.VERSION_PROPERTY;

/**
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
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
