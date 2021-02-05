package threads.thor.magnet.bencoding;

interface BEObjectBuilder<T extends BEObject> {

    boolean accept(int b);

    T build();

    BEType getType();
}
