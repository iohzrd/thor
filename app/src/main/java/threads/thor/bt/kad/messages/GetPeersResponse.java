package threads.thor.bt.kad.messages;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import threads.thor.bt.kad.BloomFilterBEP33;
import threads.thor.bt.kad.DBItem;
import threads.thor.bt.kad.DHT;
import threads.thor.bt.kad.DHT.DHTtype;


/**
 * @author Damokles
 */
public class GetPeersResponse extends AbstractLookupResponse {


    private ByteBuffer scrapeSeeds;
    private ByteBuffer scrapePeers;

    private List<DBItem> items;


    public GetPeersResponse(byte[] mtid) {
        super(mtid, Method.GET_PEERS);
    }


    /* (non-Javadoc)
     * @see threads.thor.bt.kad.messages.MessageBase#apply(threads.thor.bt.kad.DHT)
     */
    @Override
    public void apply(DHT dh_table) {
        dh_table.response(this);
    }

    @Override
    public Map<String, Object> getInnerMap() {
        Map<String, Object> innerMap = super.getInnerMap();
        if (items != null && !items.isEmpty()) {
            List<byte[]> itemsList = new ArrayList<>(items.size());
            for (DBItem item : items) {
                itemsList.add(item.getData());
            }
            innerMap.put("values", itemsList);
        }

        if (scrapePeers != null && scrapeSeeds != null) {
            innerMap.put("BFpe", scrapePeers);
            innerMap.put("BFse", scrapeSeeds);
        }

        return innerMap;
    }

    public List<DBItem> getPeerItems() {
        return items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
    }

    public void setPeerItems(List<DBItem> items) {
        this.items = items;
    }

    public void setScrapeSeeds(byte[] scrapeSeeds) {
        this.scrapeSeeds = Optional.ofNullable(scrapeSeeds).map(ByteBuffer::wrap).orElse(null);
    }

    public void setScrapeSeeds(BloomFilterBEP33 scrapeSeeds) {
        this.scrapeSeeds = scrapeSeeds != null ? scrapeSeeds.toBuffer() : null;
    }


    public void setScrapePeers(byte[] scrapePeers) {
        this.scrapePeers = Optional.ofNullable(scrapePeers).map(ByteBuffer::wrap).orElse(null);
    }

    public void setScrapePeers(BloomFilterBEP33 scrapePeers) {
        this.scrapePeers = scrapePeers != null ? scrapePeers.toBuffer() : null;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() +
                (nodes != null ? (nodes.packedSize() / DHTtype.IPV4_DHT.NODES_ENTRY_LENGTH) + " nodes | " : "") +
                (nodes6 != null ? (nodes6.packedSize() / DHTtype.IPV6_DHT.NODES_ENTRY_LENGTH) + " nodes6 | " : "") +
                (items != null ? (items.size()) + " values | " : "") +
                (scrapePeers != null ? "peer bloom filter | " : "") +
                (scrapeSeeds != null ? "seed bloom filter | " : "");
    }
}
