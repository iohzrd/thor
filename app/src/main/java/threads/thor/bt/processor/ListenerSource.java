package threads.thor.bt.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class ListenerSource {

    private final Map<ProcessingEvent, Collection<BiFunction<MagnetContext, ProcessingStage, ProcessingStage>>> listeners;

    /**
     * Create an instance of listener source for a particular type of processing context
     *
     * @since 1.5
     */
    public ListenerSource() {
        this.listeners = new HashMap<>();
    }


    /**
     * Add processing event listener.
     * <p>
     * Processing event listener is a generic {@link BiFunction},
     * that accepts the processing context and default next stage
     * and returns the actual next stage (i.e. it can also be considered a router).
     *
     * @param event    Type of processing event to be notified of
     * @param listener Routing function
     * @since 1.5
     */
    public void addListener(ProcessingEvent event, BiFunction<MagnetContext, ProcessingStage, ProcessingStage> listener) {
        listeners.computeIfAbsent(event, it -> new ArrayList<>()).add(listener);
    }

    /**
     * @param event Type of processing event
     * @return Collection of listeners, that are interested in being notified of a given event
     * @since 1.5
     */
    public Collection<BiFunction<MagnetContext, ProcessingStage, ProcessingStage>> getListeners(ProcessingEvent event) {
        Objects.requireNonNull(event);
        return listeners.getOrDefault(event, Collections.emptyList());
    }
}
