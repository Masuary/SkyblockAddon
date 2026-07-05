package yorickbm.skyblockaddon.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkContentTest {
    @Test
    void emptyAndMissingSectionsAreTreatedAsVoid() {
        assertFalse(ChunkContent.hasAnyContent(
                new String[]{null, "air"},
                "solid"::equals
        ));
    }

    @Test
    void anyNonAirBlockKeepsTheChunkTracked() {
        assertTrue(ChunkContent.hasAnyContent(
                new String[]{"air", null, "solid"},
                "solid"::equals
        ));
    }
}
