package kinor.sunshine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for the Sunshine mod.
 *
 * <p>Sunshine is a purely client-side rendering optimisation (see
 * {@code environment: "client"} in {@code fabric.mod.json}), so there is no
 * common/server entrypoint. This class exists solely to give the client code a
 * canonical place for the mod id and logger.
 */
public final class Sunshine {

    public static final String MOD_ID = "sunshine";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Sunshine() {
    }
}
