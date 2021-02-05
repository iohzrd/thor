package threads.thor.magnet.torrent;

import java.util.Comparator;

class PackedIntComparator implements Comparator<Long> {

    @Override
    public int compare(Long o1, Long o2) {
        if (o1.intValue() > o2.intValue()) {
            return 1;
        } else if (o1.intValue() < o2.intValue()) {
            return -1;
        } else {
            return Long.compare(o1 >> 32, o2 >> 32);
        }
    }
}
