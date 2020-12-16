package threads.thor.bt.runtime;


import java.util.Objects;


/**
 * Runtime builder.
 *
 * @since 1.0
 */
public class BtRuntimeBuilder {


    private final Config config;


    /**
     * Create runtime builder with provided config.
     *
     * @param config Runtime config
     * @since 1.0
     */
    public BtRuntimeBuilder(Config config) {
        this.config = Objects.requireNonNull(config, "Missing runtime config");
    }


}
