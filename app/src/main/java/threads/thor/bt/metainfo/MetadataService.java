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

package threads.thor.bt.metainfo;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import threads.LogUtils;
import threads.thor.R;
import threads.thor.bt.BtException;
import threads.thor.bt.bencoding.BEParser;
import threads.thor.bt.bencoding.BEType;
import threads.thor.bt.bencoding.model.BEList;
import threads.thor.bt.bencoding.model.BEMap;
import threads.thor.bt.bencoding.model.BEObject;
import threads.thor.bt.bencoding.model.BEObjectModel;
import threads.thor.bt.bencoding.model.BEString;
import threads.thor.bt.bencoding.model.ValidationResult;
import threads.thor.bt.bencoding.model.YamlBEObjectModelLoader;
import threads.thor.bt.service.CryptoUtil;


/**
 * <p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public final class MetadataService {
    private static final String TAG = MetadataService.class.getSimpleName();
    private static final String ANNOUNCE_KEY = "announce";
    private static final String ANNOUNCE_LIST_KEY = "announce-list";
    private static final String INFOMAP_KEY = "info";
    private static final String TORRENT_NAME_KEY = "name";
    private static final String CHUNK_SIZE_KEY = "piece length";
    private static final String CHUNK_HASHES_KEY = "pieces";
    private static final String TORRENT_SIZE_KEY = "length";
    private static final String FILES_KEY = "files";
    private static final String FILE_SIZE_KEY = "length";
    private static final String FILE_PATH_ELEMENTS_KEY = "path";
    private static final String PRIVATE_KEY = "private";
    private static final String CREATION_DATE_KEY = "creation date";
    private static final String CREATED_BY_KEY = "created by";
    private final Charset defaultCharset;
    private final BEObjectModel torrentModel;
    private final BEObjectModel infodictModel;

    public MetadataService(@NonNull Context context) {
        this.defaultCharset = StandardCharsets.UTF_8;

        try {
            try (InputStream in = context.getResources().openRawResource(R.raw.metainfo)) {
                this.torrentModel = new YamlBEObjectModelLoader().load(in);
            }
            try (InputStream in = context.getResources().openRawResource(R.raw.infodict)) {
                this.infodictModel = new YamlBEObjectModelLoader().load(in);
            }
        } catch (IOException e) {
            throw new BtException("Failed to create metadata service", e);
        }
    }


    public Torrent fromByteArray(byte[] bs) {
        return buildTorrent(bs);
    }


    private Torrent buildTorrent(byte[] bs) {
        try (BEParser parser = new BEParser(bs)) {
            if (parser.readType() != BEType.MAP) {
                throw new BtException("Invalid metainfo format -- expected a map, got: "
                        + parser.readType().name().toLowerCase());
            }

            BEMap metadata = parser.readMap();

            ValidationResult validationResult = torrentModel.validate(metadata);
            if (!validationResult.isSuccess()) {
                ValidationResult infodictValidationResult = infodictModel.validate(metadata);
                if (!infodictValidationResult.isSuccess()) {
                    throw new BtException("Validation failed for  metainfo:\n1. Standard model: "
                            + Arrays.toString(validationResult.getMessages().toArray())
                            + "\n2. Standalone info dictionary model: " + Arrays.toString(infodictValidationResult.getMessages().toArray()));
                }
            }

            BEMap infoDictionary;
            TorrentSource source;

            Map<String, BEObject<?>> root = metadata.getValue();
            if (root.containsKey(INFOMAP_KEY)) {
                // standard BEP-3 format
                infoDictionary = (BEMap) root.get(INFOMAP_KEY);
                source = new TorrentSource() {
                    @Override
                    public Optional<byte[]> getMetadata() {
                        return Optional.of(metadata.getContent());
                    }

                    @Override
                    public byte[] getExchangedMetadata() {
                        return infoDictionary.getContent();
                    }
                };
            } else {
                // BEP-9 exchanged metadata (just the info dictionary)
                infoDictionary = metadata;
                source = new TorrentSource() {
                    @Override
                    public Optional<byte[]> getMetadata() {
                        return Optional.empty();
                    }

                    @Override
                    public byte[] getExchangedMetadata() {
                        return infoDictionary.getContent();
                    }
                };
            }


            TorrentId torrentId = TorrentId.fromBytes(CryptoUtil.getSha1Digest(
                    infoDictionary.getContent()));

            Map<String, BEObject<?>> infoMap = infoDictionary.getValue();

            String name = "";
            if (infoMap.get(TORRENT_NAME_KEY) != null) {
                byte[] data = (byte[]) infoMap.get(TORRENT_NAME_KEY).getValue();
                name = new String(data, defaultCharset);
            }

            BigInteger chunkSize = (BigInteger) infoMap.get(CHUNK_SIZE_KEY).getValue();


            byte[] chunkHashes = (byte[]) infoMap.get(CHUNK_HASHES_KEY).getValue();


            List<TorrentFile> torrentFiles = new ArrayList<>();
            long size;
            if (infoMap.get(TORRENT_SIZE_KEY) != null) {
                BigInteger torrentSize = (BigInteger) infoMap.get(TORRENT_SIZE_KEY).getValue();
                size = torrentSize.longValue();

            } else {
                List<BEMap> files = (List<BEMap>) infoMap.get(FILES_KEY).getValue();
                BigInteger torrentSize = BigInteger.ZERO;
                for (BEMap file : files) {

                    Map<String, BEObject<?>> fileMap = file.getValue();

                    BigInteger fileSize = (BigInteger) fileMap.get(FILE_SIZE_KEY).getValue();
                    TorrentFile torrentFile = new TorrentFile(fileSize.longValue());


                    torrentSize = torrentSize.add(fileSize);

                    List<BEString> pathElements = (List<BEString>)
                            fileMap.get(FILE_PATH_ELEMENTS_KEY).getValue();

                    torrentFile.setPathElements(pathElements.stream()
                            .map(bytes -> bytes.getValue(defaultCharset))
                            .collect(Collectors.toList()));

                    torrentFiles.add(torrentFile);
                }

                size = torrentSize.longValue();
            }
            Torrent torrent = Torrent.createTorrent(torrentId, name, source, torrentFiles,
                    chunkHashes, size, chunkSize.longValue());


            boolean isPrivate = false;
            if (infoMap.get(PRIVATE_KEY) != null) {
                if (BigInteger.ONE.equals(infoMap.get(PRIVATE_KEY).getValue())) {
                    torrent.setPrivate(true);
                    isPrivate = true;
                }
            }

            if (root.get(CREATION_DATE_KEY) != null) {

                // TODO: some torrents contain bogus values here (like 101010101010), which causes an exception
                try {
                    BigInteger epochMilli = (BigInteger) root.get(CREATION_DATE_KEY).getValue();
                    torrent.setCreationDate(epochMilli.intValue());
                } catch (Throwable e) {
                    LogUtils.error(TAG, e);
                }
            }

            if (root.get(CREATED_BY_KEY) != null) {
                byte[] createdBy = (byte[]) root.get(CREATED_BY_KEY).getValue();
                torrent.setCreatedBy(new String(createdBy, defaultCharset));
            }

            // TODO: support for private torrents with multiple trackers
            if (!isPrivate && root.containsKey(ANNOUNCE_LIST_KEY)) {

                List<List<String>> trackerUrls;

                BEList announceList = (BEList) root.get(ANNOUNCE_LIST_KEY);
                List<BEList> tierList = (List<BEList>) announceList.getValue();
                trackerUrls = new ArrayList<>(tierList.size() + 1);
                for (BEList tierElement : tierList) {

                    List<String> tierTackerUrls;

                    List<BEString> trackerUrlList = (List<BEString>) tierElement.getValue();
                    tierTackerUrls = new ArrayList<>(trackerUrlList.size() + 1);
                    for (BEString trackerUrlElement : trackerUrlList) {
                        tierTackerUrls.add(trackerUrlElement.getValue(defaultCharset));
                    }
                    trackerUrls.add(tierTackerUrls);
                }

            } else if (root.containsKey(ANNOUNCE_KEY)) {
                byte[] trackerUrl = (byte[]) root.get(ANNOUNCE_KEY).getValue();
                LogUtils.error(TAG, new String(trackerUrl));
            }

            return torrent;

        } catch (Exception e) {
            throw new BtException("Invalid metainfo format", e);
        }
    }
}
