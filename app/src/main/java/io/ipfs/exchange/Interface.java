package io.ipfs.exchange;


import io.ipfs.bitswap.BitSwapReceiver;
public interface Interface extends Fetcher, BitSwapReceiver {
    void reset();

}
