package threads.thor.magnet.protocol.extended;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import threads.thor.magnet.bencoding.BEInteger;
import threads.thor.magnet.bencoding.BEMap;
import threads.thor.magnet.bencoding.BEObject;
import threads.thor.magnet.bencoding.BEParser;
import threads.thor.magnet.bencoding.BEType;
import threads.thor.magnet.net.Peer;
import threads.thor.magnet.net.buffer.ByteBufferView;
import threads.thor.magnet.protocol.DecodingContext;
import threads.thor.magnet.protocol.EncodingContext;
import threads.thor.magnet.protocol.InvalidMessageException;
import threads.thor.magnet.protocol.handler.MessageHandler;

class ExtendedHandshakeMessageHandler implements MessageHandler<ExtendedHandshake> {

    private final Collection<Class<? extends ExtendedHandshake>> supportedTypes;
    private final ConcurrentMap<Peer, Map<Integer, String>> peerTypeMappings;

    ExtendedHandshakeMessageHandler() {
        peerTypeMappings = new ConcurrentHashMap<>();
        supportedTypes = Collections.singleton(ExtendedHandshake.class);
    }

    @Override
    public Collection<Class<? extends ExtendedHandshake>> getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public Class<? extends ExtendedHandshake> readMessageType(ByteBufferView buffer) {
        return ExtendedHandshake.class;
    }

    private void processTypeMapping(Peer peer, BEObject mappingObj) {

        if (mappingObj == null) {
            return;
        }

        // TODO: use BE generic validation
        if (mappingObj.getType() != BEType.MAP) {
            throw new InvalidMessageException("Extended message types mapping must be a dictionary." +
                    " Actual BE type: " + mappingObj.getType().name());
        }

        @SuppressWarnings("unchecked")
        Map<String, BEObject> mapping = (Map<String, BEObject>) mappingObj.getValue();
        if (mapping.size() > 0) {
            // according to BEP-10, peers are only required to send a delta of changes
            // on subsequent handshakes, so we need to store all mappings received from the peer
            // and merge the changes..
            //
            // subsequent handshake messages can be used to enable/disable extensions
            // without restarting the connection
            peerTypeMappings.put(peer, mergeMappings(peerTypeMappings.getOrDefault(peer, new HashMap<>()), mapping));
        }
    }

    Map<Integer, String> getPeerTypeMapping(Peer peer) {
        Map<Integer, String> mapping = peerTypeMappings.get(peer);
        return (mapping == null) ? Collections.emptyMap() : Collections.unmodifiableMap(mapping);
    }

    private Map<Integer, String> mergeMappings(Map<Integer, String> existing, Map<String, BEObject> changes) {

        for (Map.Entry<String, BEObject> entry : changes.entrySet()) {
            String typeName = entry.getKey();
            int typeId = ((BEInteger) entry.getValue()).getValue().intValue();
            if (typeId == 0) {
                // by setting type ID to 0 peer signals that he has disabled this extension
                Iterator<Integer> iter = existing.keySet().iterator();
                while (iter.hasNext()) {
                    Integer key = iter.next();
                    if (typeName.equals(existing.get(key))) {
                        iter.remove();
                        break;
                    }
                }
            } else {
                existing.put(typeId, typeName);
            }
        }
        return existing;
    }

    @Override
    public int decode(DecodingContext context, ByteBufferView buffer) {

        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        try (BEParser parser = new BEParser(payload)) {
            BEMap message = parser.readMap();

            Map<String, BEObject<?>> value = message.getValue();
            processTypeMapping(context.getPeer(), value.get(ExtendedHandshake.MESSAGE_TYPE_MAPPING_KEY));
            context.setMessage(new ExtendedHandshake(value));
            return message.getContent().length;
        } catch (Exception e) {
            // TODO: parser should be configurable to return null instead of throwing an exception;
            // otherwise need to treat exceptions differently depending on their type
            // (i.e. incorrect message vs incomplete message)
            throw new RuntimeException("Failed to decode extended handshake", e);
        }
    }

    @Override
    public boolean encode(EncodingContext context, ExtendedHandshake message, ByteBuffer buffer) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new BEMap(null, message.getData()).writeTo(out);
        } catch (IOException e) {
            // can't happen
        }

        byte[] payload = out.toByteArray();
        if (buffer.remaining() < payload.length) {
            return false;
        }

        buffer.put(payload);
        return true;
    }
}
