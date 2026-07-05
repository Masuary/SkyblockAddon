package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceManagerTest {
    @Test
    void backsUpExistingConfigurationAndPreservesLegacyCategoryFiles(@TempDir final Path configDirectory)
            throws Exception {
        final Path modDirectory = Files.createDirectories(configDirectory.resolve("skyblockaddon"));
        final Path marker = modDirectory.resolve("server-custom.txt");
        Files.writeString(marker, "original");
        final Path permissionDirectory = Files.createDirectories(modDirectory.resolve("registries/permissions"));
        final Path legacyCategory = permissionDirectory.resolve("general.json");
        Files.writeString(legacyCategory, "{\"permissions\":[]}");

        ResourceManager.commonSetup(configDirectory);

        final Path fullBackup = configDirectory.resolve("skyblockaddon.pre-10.0-backup");
        assertEquals("original", Files.readString(fullBackup.resolve("server-custom.txt")));
        assertTrue(Files.isRegularFile(
                permissionDirectory.resolve("legacy-category-backup/general.json")
        ));
        assertFalse(Files.exists(legacyCategory));
        assertTrue(Files.isRegularFile(permissionDirectory.resolve("minecraft.json")));

        Files.writeString(marker, "changed-after-backup");
        ResourceManager.commonSetup(configDirectory);
        assertEquals("original", Files.readString(fullBackup.resolve("server-custom.txt")));
    }
}
