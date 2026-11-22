package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnPointPlacementTest {

    @Test
    void standingOnASolidBlockOnTheIslandIsAllowed() {
        assertEquals(SpawnPointPlacement.ALLOWED, SpawnPointPlacement.evaluate(true, true, true));
    }

    @Test
    void fallingOrJumpingIsRefused() {
        assertEquals(SpawnPointPlacement.NOT_ON_GROUND, SpawnPointPlacement.evaluate(true, false, true));
        assertEquals(SpawnPointPlacement.NOT_ON_GROUND, SpawnPointPlacement.evaluate(true, false, false));
    }

    @Test
    void groundWithoutASolidBlockUnderTheFeetIsRefused() {
        assertEquals(SpawnPointPlacement.NOT_ON_GROUND, SpawnPointPlacement.evaluate(true, true, false));
    }

    @Test
    void outsideTheIslandIsRefusedFirst() {
        assertEquals(SpawnPointPlacement.OUTSIDE_ISLAND, SpawnPointPlacement.evaluate(false, true, true));
        assertEquals(SpawnPointPlacement.OUTSIDE_ISLAND, SpawnPointPlacement.evaluate(false, false, false));
    }

    @Test
    void refusalsNameTheirMessage() {
        assertEquals("island.setspawn.outside", SpawnPointPlacement.OUTSIDE_ISLAND.languageKey());
        assertEquals("island.setspawn.not.on.ground", SpawnPointPlacement.NOT_ON_GROUND.languageKey());
    }
}
