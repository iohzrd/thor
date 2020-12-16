package threads.thor.bt;

import java.util.Objects;
import java.util.function.Supplier;

import threads.thor.bt.processor.ProcessingContext;
import threads.thor.bt.processor.Processor;
import threads.thor.bt.processor.TorrentProcessorFactory;
import threads.thor.bt.processor.listener.ListenerSource;
import threads.thor.bt.runtime.BtClient;
import threads.thor.bt.runtime.BtRuntime;

/**
 * Provides basic capabilities to build a Bt client.
 *
 * @since 1.1
 */
public abstract class BaseClientBuilder<B extends BaseClientBuilder> {

    private BtRuntime runtime;


    /**
     * @since 1.1
     */
    BaseClientBuilder() {
    }

    /**
     * Set the runtime that the newly built client will be attached to.
     *
     * @param runtime Bt runtime
     * @since 1.4
     */
    @SuppressWarnings("unchecked")
    public B runtime(BtRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "Missing runtime");
        return (B) this;
    }

    /**
     * @since 1.1
     */
    public BtClient build() {
        Objects.requireNonNull(runtime, "Missing runtime");
        Supplier<BtClient> clientSupplier = () -> buildClient(runtime, buildProcessingContext(runtime));
        return new LazyClient(clientSupplier);
    }

    /**
     * @since 1.4
     */
    protected abstract ProcessingContext buildProcessingContext(BtRuntime runtime);

    private <C extends ProcessingContext> BtClient buildClient(BtRuntime runtime, C context) {

        ListenerSource<C> listenerSource = new ListenerSource<>();
        collectStageListeners(listenerSource);

        return new DefaultClient<>(runtime, processor(runtime), context, listenerSource);
    }

    /**
     * @since 1.5
     */
    protected abstract <C extends ProcessingContext> void collectStageListeners(ListenerSource<C> listenerSource);

    private <C extends ProcessingContext> Processor processor(BtRuntime runtime) {

        return (Processor) TorrentProcessorFactory.createMagnetProcessor(runtime);
    }
}
