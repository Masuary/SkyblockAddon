package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.util.BanNameResolution.Decision;
import yorickbm.skyblockaddon.core.util.BanNameResolution.RemoteLookup;
import yorickbm.skyblockaddon.core.util.BanNameResolution.Reply;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanNameResolutionTest {

    private static final UUID MOJANG_ACCOUNT = UUID.fromString("d2bfc26f-9a30-4124-93cc-0da1631278ae");
    private static final UUID OTHER_MOJANG_ACCOUNT = UUID.fromString("8f0d6a87-9d9e-4fe6-84d3-0b2473a7692f");
    private static final UUID OFFLINE_PLAYER = UUID.nameUUIDFromBytes("OfflinePlayer:LocalPlayer".getBytes(StandardCharsets.UTF_8));

    @Test
    void onlyValidMinecraftNamesAreLookedUp() {
        assertTrue(BanNameResolution.isPossibleName("Crowley_EU9"));
        assertFalse(BanNameResolution.isPossibleName("no.such+name"));
        assertFalse(BanNameResolution.isPossibleName("ThisNameIsTooLong17"));
        assertFalse(BanNameResolution.isPossibleName(""));
    }

    @Test
    void knownOfflinePlayerIsBannedWithoutALookup() {
        assertFalse(BanNameResolution.needsRemoteLookup(Optional.of(OFFLINE_PLAYER)));
        assertEquals(new Decision(Reply.BAN, Optional.of(OFFLINE_PLAYER)),
                BanNameResolution.decide(Optional.of(OFFLINE_PLAYER), RemoteLookup.NOT_ASKED, Optional.empty()));
    }

    @Test
    void knownMojangAccountIsConfirmedAtMojang() {
        assertTrue(BanNameResolution.needsRemoteLookup(Optional.of(MOJANG_ACCOUNT)));
        assertTrue(BanNameResolution.needsRemoteLookup(Optional.empty()));
    }

    @Test
    void mojangAnswerWinsOverAStaleKnownName() {
        assertEquals(new Decision(Reply.BAN, Optional.of(OTHER_MOJANG_ACCOUNT)),
                BanNameResolution.decide(Optional.of(MOJANG_ACCOUNT), RemoteLookup.FOUND, Optional.of(OTHER_MOJANG_ACCOUNT)));
    }

    @Test
    void failedLookupFallsBackToTheKnownPlayer() {
        assertEquals(new Decision(Reply.BAN, Optional.of(MOJANG_ACCOUNT)),
                BanNameResolution.decide(Optional.of(MOJANG_ACCOUNT), RemoteLookup.FAILED, Optional.empty()));
    }

    @Test
    void failedLookupForAnUnknownNameAsksToTryAgain() {
        assertEquals(new Decision(Reply.LOOKUP_FAILED, Optional.empty()),
                BanNameResolution.decide(Optional.empty(), RemoteLookup.FAILED, Optional.empty()));
    }

    @Test
    void nameMojangDoesNotKnowIsUnknown() {
        assertEquals(new Decision(Reply.UNKNOWN_PLAYER, Optional.empty()),
                BanNameResolution.decide(Optional.empty(), RemoteLookup.NOT_FOUND, Optional.empty()));
        assertEquals(new Decision(Reply.UNKNOWN_PLAYER, Optional.empty()),
                BanNameResolution.decide(Optional.of(MOJANG_ACCOUNT), RemoteLookup.NOT_FOUND, Optional.empty()));
    }
}
