package yorickbm.skyblockaddon;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.util.exceptions.TerralithFoundException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyBlockAddonCompatibilityTest {
    @Test
    void terralithPreventsStartup() {
        final TerralithFoundException exception = assertThrows(
                TerralithFoundException.class,
                () -> SkyBlockAddon.rejectIncompatibleTerralith("terralith"::equals)
        );

        assertTrue(exception.getMessage().contains("corrupt the island world"));
    }

    @Test
    void unrelatedModsDoNotPreventStartup() {
        assertDoesNotThrow(() -> SkyBlockAddon.rejectIncompatibleTerralith(modId -> false));
    }
}
