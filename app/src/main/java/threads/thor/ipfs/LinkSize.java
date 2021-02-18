package threads.thor.ipfs;

public class LinkSize {

    private final long size;


    private LinkSize(long size) {
        this.size = size;
    }

    public static LinkSize create(long size) {

        return new LinkSize(size);
    }


    public long getSize() {
        return size;
    }


}

