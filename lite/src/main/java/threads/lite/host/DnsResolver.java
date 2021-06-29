package threads.lite.host;

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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import threads.lite.LogUtils;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.Protocol;


public class DnsResolver {
    public static final String DNS_ADDR = "dnsaddr=";
    public static final String DNS_LINK = "dnslink=";
    private static final String IPv4 = "/ip4/";
    private static final String IPv6 = "/ip6/";
    private static final String DNS_ADDR_PATH = "/dnsaddr/";
    private static final String DNS4_PATH = "/dns4/";
    private static final String DNS6_PATH = "/dns6/";
    private static final String TAG = DnsResolver.class.getSimpleName();
    private static DnsClient INSTANCE = null;


    @NonNull
    public static String resolveDnsLink(@NonNull String host) {

        List<String> txtRecords = getTxtRecords("_dnslink.".concat(host));
        for (String txtRecord : txtRecords) {
            try {
                if (txtRecord.startsWith(DNS_LINK)) {
                    return txtRecord.replaceFirst(DNS_LINK, "");
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
        return "";
    }

    @NonNull
    private static List<String> getTxtRecords(@NonNull String host) {
        List<String> txtRecords = new ArrayList<>();
        try {
            DnsClient client = getInstance();
            DnsQueryResult result = client.query(host, Record.TYPE.TXT);
            DnsMessage response = result.response;
            List<Record<? extends Data>> records = response.answerSection;
            for (Record<? extends Data> record : records) {
                Data payload = record.getPayload();
                if (payload instanceof TXT) {
                    TXT text = (TXT) payload;
                    txtRecords.add(text.getText());
                } else {
                    LogUtils.warning(TAG, payload.toString());
                }
            }
        } catch (Throwable throwable) {
            LogUtils.debug(TAG, "" + throwable.getClass().getName());
        }
        return txtRecords;
    }

    @NonNull
    public static String resolveDns4Address(@NonNull String multiaddress) throws UnknownHostException {
        if (!multiaddress.startsWith(DNS4_PATH)) {
            throw new RuntimeException();
        }
        String query = multiaddress.replaceFirst(DNS4_PATH, "");
        String host = query.split("/")[0];
        InetAddress address = InetAddress.getByName(host);
        String ip = IPv4;
        if (address instanceof Inet6Address) {
            ip = IPv6;
        }
        String hostAddress = address.getHostAddress();
        return ip.concat(query.replaceFirst(host, hostAddress));
    }

    @NonNull
    public static String resolveDns6Address(@NonNull String multiaddress) throws UnknownHostException {
        if (!multiaddress.startsWith(DNS6_PATH)) {
            throw new RuntimeException();
        }
        String query = multiaddress.replaceFirst(DNS6_PATH, "");
        String host = query.split("/")[0];
        InetAddress address = InetAddress.getByName(host);
        String ip = IPv4;
        if (address instanceof Inet6Address) {
            ip = IPv6;
        }
        String hostAddress = address.getHostAddress();
        return ip.concat(query.replaceFirst(host, hostAddress));
    }

    @NonNull
    public static Multiaddr resolveDns6(@NonNull Multiaddr multiaddr) throws UnknownHostException {
        return new Multiaddr(resolveDns6Address(multiaddr.toString()));
    }

    @NonNull
    public static Multiaddr resolveDns4(@NonNull Multiaddr multiaddr) throws UnknownHostException {
        return new Multiaddr(resolveDns4Address(multiaddr.toString()));
    }

    @NonNull
    public static List<Multiaddr> resolveDnsAddress(@NonNull Multiaddr multiaddr) {
        List<Multiaddr> mAddrs = new ArrayList<>();
        String host = multiaddr.getStringComponent(Protocol.Type.DNSADDR);
        if (host != null) {
            Set<String> addresses = resolveDnsAddress(host);
            String peerId = multiaddr.getStringComponent(Protocol.Type.P2P);
            for (String addr : addresses) {
                if (peerId != null) {
                    if (addr.endsWith(peerId)) {
                        try {
                            mAddrs.add(new Multiaddr(addr));
                        } catch (Throwable throwable) {
                            LogUtils.verbose(TAG, throwable.getClass().getSimpleName());
                        }
                    }
                }
            }
        }
        return mAddrs;
    }


    @NonNull
    public static Set<String> resolveDnsAddress(@NonNull String host) {
        return resolveDnsAddressInternal(host, new HashSet<>());
    }

    @NonNull
    public static Set<String> resolveDnsAddressInternal(
            @NonNull String host, @NonNull Set<String> hosts) {
        Set<String> multiAddresses = new HashSet<>();

        // recursion protection
        if (hosts.contains(host)) {
            hosts.add(host);
        }

        List<String> txtRecords = getTxtRecords("_dnsaddr." + host);
        for (String txtRecord : txtRecords) {
            try {
                if (txtRecord.startsWith(DNS_ADDR)) {
                    String testRecordReduced = txtRecord.replaceFirst(DNS_ADDR, "");
                    if (testRecordReduced.startsWith(DNS_ADDR_PATH)) {
                        String query = testRecordReduced.replaceFirst(DNS_ADDR_PATH, "");
                        String child = query.split("/")[0];
                        multiAddresses.addAll(resolveDnsAddressInternal(child, hosts));
                    } else if (testRecordReduced.startsWith(DNS4_PATH)) {
                        multiAddresses.add(resolveDns4Address(testRecordReduced));
                    } else if (testRecordReduced.startsWith(DNS6_PATH)) {
                        multiAddresses.add(resolveDns6Address(testRecordReduced));
                    } else {
                        multiAddresses.add(testRecordReduced);
                    }
                }
            } catch (Throwable throwable) {
                LogUtils.error(TAG, throwable);
            }
        }
        return multiAddresses;
    }

    @NonNull
    public static DnsClient getInstance() {
        if (INSTANCE == null) {
            synchronized (TAG.intern()) {
                if (INSTANCE == null) {
                    try {
                        INSTANCE = new DnsClient(new LruCache(128));
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return INSTANCE;
    }

}
