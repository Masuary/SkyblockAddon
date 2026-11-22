package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnownPlayerNamesTest {

    private static final UUID CROWLEY = UUID.fromString("d2bfc26f-9a30-4124-93cc-0da1631278ae");
    private static final UUID NALTHORIK = UUID.fromString("8f0d6a87-9d9e-4fe6-84d3-0b2473a7692f");
    private static final UUID OLD_NOTCH = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NEW_NOTCH = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void knownNameResolvesIgnoringCase() {
        assertEquals(Optional.of(CROWLEY), KnownPlayerNames.find(Map.of(CROWLEY, "CrowleyEU", NALTHORIK, "Nalthorik"), "crowleyeu"));
    }

    @Test
    void unknownNameResolvesToNothing() {
        assertEquals(Optional.empty(), KnownPlayerNames.find(Map.of(CROWLEY, "CrowleyEU"), "Zq9xNoSuchPlayer77"));
    }

    @Test
    void exactCaseBreaksATieBetweenTwoKnownPlayers() {
        assertEquals(Optional.of(NEW_NOTCH), KnownPlayerNames.find(Map.of(OLD_NOTCH, "notch", NEW_NOTCH, "Notch"), "Notch"));
    }

    @Test
    void ambiguousNameWithoutAnExactMatchResolvesToNothing() {
        assertEquals(Optional.empty(), KnownPlayerNames.find(Map.of(OLD_NOTCH, "notch", NEW_NOTCH, "NOTCH"), "Notch"));
    }

    @Test
    void sameExactNameOnTwoKnownPlayersResolvesToNothing() {
        assertEquals(Optional.empty(), KnownPlayerNames.find(Map.of(OLD_NOTCH, "Notch", NEW_NOTCH, "Notch"), "Notch"));
    }
}
