package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceManagerTest {
    private static final RegistrySelector DEFAULT_PERMISSIONS = new RegistrySelector(Map.of(
            "PermissionRegistry", () -> "registries/permissions/default.json"));

    @TempDir
    Path configFolder;

    @Test
    void missingIslandTemplateIsGeneratedAndReported() throws IOException {
        Files.createDirectories(configFolder.resolve(SkyblockAddonCore.MOD_ID));

        final List<String> generatedFiles = ResourceManager.commonSetup(configFolder, DEFAULT_PERMISSIONS);

        assertTrue(generatedFiles.contains(ResourceManager.ISLAND_TEMPLATE_FILE));
        assertTrue(Files.size(configFolder.resolve(SkyblockAddonCore.MOD_ID).resolve(ResourceManager.ISLAND_TEMPLATE_FILE)) > 0);
    }

    @Test
    void existingFilesAreKeptAndNotReported() throws IOException {
        Files.createDirectories(configFolder.resolve(SkyblockAddonCore.MOD_ID));
        ResourceManager.commonSetup(configFolder, DEFAULT_PERMISSIONS);

        final List<String> generatedFiles = ResourceManager.commonSetup(configFolder, DEFAULT_PERMISSIONS);

        assertFalse(generatedFiles.contains(ResourceManager.ISLAND_TEMPLATE_FILE));
        assertTrue(generatedFiles.isEmpty());
    }
}
