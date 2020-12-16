package threads.thor.bt.processor.listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import threads.thor.bt.processor.ProcessingContext;
import threads.thor.bt.processor.ProcessingStage;

public class ListenerSource<C extends ProcessingContext> {

    private final Map<ProcessingEvent, Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>>> listeners;

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
    public void addListener(ProcessingEvent event, BiFunction<C, ProcessingStage<C>, ProcessingStage<C>> listener) {
        listeners.computeIfAbsent(event, it -> new ArrayList<>()).add(listener);
    }

    /**
     * @param event Type of processing event
     * @return Collection of listeners, that are interested in being notified of a given event
     * @since 1.5
     */
    public Collection<BiFunction<C, ProcessingStage<C>, ProcessingStage<C>>> getListeners(ProcessingEvent event) {
        Objects.requireNonNull(event);
        return listeners.getOrDefault(event, Collections.emptyList());
    }
}
