package threads.thor.bt.kad.messages;

import java.util.Map;

import threads.thor.bt.kad.DHT;
import threads.thor.bt.kad.Key;

/**
 * @author Damokles
 */
public class GetPeersRequest extends AbstractLookupRequest {


    private boolean noSeeds;
    private boolean scrape;


    public GetPeersRequest(Key info_hash) {
        super(info_hash, Method.GET_PEERS);
    }

    /* (non-Javadoc)
     * @see threads.thor.bt.kad.messages.MessageBase#apply(threads.thor.bt.kad.DHT)
     */
    @Override
    public void apply(DHT dh_table) {
        dh_table.getPeers(this);
    }

    @Override
    public Map<String, Object> getInnerMap() {
        Map<String, Object> innerMap = super.getInnerMap();

        if (noSeeds)
            innerMap.put("noseed", 1L);
        if (scrape)
            innerMap.put("scrape", 1L);

        return innerMap;
    }

    public boolean isNoSeeds() {
        return noSeeds;
    }

    public void setNoSeeds(boolean noSeeds) {
        this.noSeeds = noSeeds;
    }

    public boolean isScrape() {
        return scrape;
    }

    public void setScrape(boolean scrape) {
        this.scrape = scrape;
    }

    @Override
    protected String targetBencodingName() {
        return "info_hash";
    }
}
