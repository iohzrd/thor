package threads.thor.bt.bencoding;

interface BEObjectBuilder<T extends BEObject> {

    boolean accept(int b);

    T build();

    BEType getType();
}
