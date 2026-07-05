package yorickbm.skyblockaddon.mixins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinConnectorTest {
    private static boolean sentinelInitialized;

    @Test
    void classResourceDetectionDoesNotLoadTheTargetClass() {
        assertTrue(MixinConnector.isClassResourcePresent(Sentinel.class.getName()));
        assertFalse(sentinelInitialized);
    }

    @Test
    void missingClassResourceIsRejected() {
        assertFalse(MixinConnector.isClassResourcePresent("missing.skyblockaddon.OptionalTarget"));
    }

    @Test
    void optionalConfigurationsLoadOnlyForPresentTargets() {
        final List<String> available = MixinConnector.availableOptionalConfigurations(
                "com.hollingsworth.arsnouveau.common.block.tile.PortalTile"::equals
        );

        assertEquals(List.of("/mixins/ars_nouveau.mixin.json"), available);
        assertTrue(MixinConnector.availableOptionalConfigurations(target -> false).isEmpty());
        assertEquals(10, MixinConnector.availableOptionalConfigurations(target -> true).size());
    }

    private static final class Sentinel {
        static {
            sentinelInitialized = true;
        }
    }
}
