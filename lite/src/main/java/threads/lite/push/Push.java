package threads.lite.push;

import androidx.annotation.NonNull;

import threads.lite.cid.PeerId;

public interface Push {
    void push(@NonNull PeerId peerId, @NonNull String content);
}
