package threads.lite.host;

import androidx.annotation.NonNull;

import threads.lite.cid.PeerId;

public interface ConnectionHandler {
    void connected(@NonNull PeerId peerId);
}
