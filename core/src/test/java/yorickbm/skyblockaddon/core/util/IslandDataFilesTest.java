package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandDataFilesTest {
    private static final UUID ISLAND_ID = UUID.fromString("004f267c-1a2b-4c3d-8e9f-0123456789ab");

    @Test
    void islandFileNameYieldsItsId() {
        assertEquals(Optional.of(ISLAND_ID), IslandDataFiles.islandIdFromFileName(IslandDataFiles.fileNameFor(ISLAND_ID)));
    }

    @Test
    void copiesBackupsAndTempFilesAreNotIslandFiles() {
        assertEquals(Optional.empty(), IslandDataFiles.islandIdFromFileName(ISLAND_ID + " copy.nbt"));
        assertEquals(Optional.empty(), IslandDataFiles.islandIdFromFileName(ISLAND_ID + ".nbt.tmp"));
        assertEquals(Optional.empty(), IslandDataFiles.islandIdFromFileName(ISLAND_ID + ".nbt.bak"));
        assertEquals(Optional.empty(), IslandDataFiles.islandIdFromFileName("island.nbt"));
    }
}
