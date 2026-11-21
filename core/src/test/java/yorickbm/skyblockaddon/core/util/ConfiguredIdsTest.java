package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredIdsTest {

    @Test
    void commaSeparatedIdsAreTrimmedAndLowerCased() {
        assertEquals(Set.of("minecraft:jukebox", "create:contraption_controls"),
                ConfiguredIds.parse(" minecraft:Jukebox ,create:contraption_controls"));
    }

    @Test
    void emptyEntriesAreIgnored() {
        assertTrue(ConfiguredIds.parse("").isEmpty());
        assertEquals(Set.of("minecraft:egg"), ConfiguredIds.parse(",, minecraft:egg ,"));
    }
}
