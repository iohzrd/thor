package threads.thor.ipfs;

import androidx.annotation.NonNull;

import org.minidns.DnsClient;
import org.minidns.cache.LruCache;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsqueryresult.DnsQueryResult;
import org.minidns.record.Data;
import org.minidns.record.Record;
import org.minidns.record.TXT;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import threads.LogUtils;
import threads.thor.Settings;

public class DnsAddrResolver {

    private static final String IPv4 = "/ip4/";
    private static final String IPv6 = "/ip6/";
    private static final String TAG = DnsAddrResolver.class.getSimpleName();

    @NonNull
    public static List<String> getMultiAddresses() {
        List<String> multiAddresses = new ArrayList<>();
        List<String> txtRecords = getTxtRecords();
        for (String txtRecord : txtRecords) {
            try {
                if (txtRecord.startsWith(Settings.DNS_ADDR)) {
                    String multiAddress = txtRecord.replaceFirst(Settings.DNS_ADDR, "");
                    // now get IP of multiAddress
                    String host = multiAddress.substring(0, multiAddress.indexOf("/"));
                    if (!host.isEmpty()) {
                        String data = multiAddress.substring(host.length());
                        InetAddress address = InetAddress.getByName(host);
                        String ip = IPv4;
                        if (address instanceof Inet6Address) {
                            ip = IPv6;
                        }
                        String hostAddress = address.getHostAddress();

                        if (!data.startsWith("/p2p/")) {
                            String newAddress = hostAddress.concat(data);
                            multiAddresses.add(ip.concat(newAddress));
                        }
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
        return multiAddresses;
    }


    @NonNull
    private static List<String> getTxtRecords() {
        List<String> txtRecords = new ArrayList<>();
        try {
            DnsClient client = new DnsClient(new LruCache(0));
            DnsQueryResult result = client.query(Settings.LIB2P_DNS, Record.TYPE.TXT);
            DnsMessage response = result.response;
            List<Record<? extends Data>> records = response.answerSection;
            for (Record<? extends Data> record : records) {
                TXT text = (TXT) record.getPayload();
                txtRecords.add(text.getText());
            }
        } catch (Throwable e) {
            LogUtils.error(TAG, "" + e.getLocalizedMessage(), e);
        }
        return txtRecords;
    }
}
