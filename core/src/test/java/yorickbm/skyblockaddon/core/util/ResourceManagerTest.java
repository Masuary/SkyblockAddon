package yorickbm.skyblockaddon.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceManagerTest {

    @TempDir
    Path configFolder;

    @Test
    void missingIslandTemplateIsGeneratedAndReported() throws IOException {
        Files.createDirectories(configFolder.resolve(SkyblockAddonCore.MOD_ID));

        final List<String> generatedFiles = ResourceManager.commonSetup(configFolder, BundledDefaultsPack.DEFAULT);

        assertTrue(generatedFiles.contains(ResourceManager.ISLAND_TEMPLATE_FILE));
        assertTrue(Files.size(configFolder.resolve(SkyblockAddonCore.MOD_ID).resolve(ResourceManager.ISLAND_TEMPLATE_FILE)) > 0);
    }

    @Test
    void existingFilesAreKeptAndNotReported() throws IOException {
        Files.createDirectories(configFolder.resolve(SkyblockAddonCore.MOD_ID));
        ResourceManager.commonSetup(configFolder, BundledDefaultsPack.DEFAULT);

        final List<String> generatedFiles = ResourceManager.commonSetup(configFolder, BundledDefaultsPack.DEFAULT);

        assertFalse(generatedFiles.contains(ResourceManager.ISLAND_TEMPLATE_FILE));
        assertTrue(generatedFiles.isEmpty());
    }

    @Test
    void woldsVaultsWinsOverVaultHuntersBecauseItLoadsBoth() {
        assertEquals(BundledDefaultsPack.WOLDS_VAULTS, BundledDefaultsPack.select(Set.of("woldsvaults", "the_vault")::contains));
        assertEquals(BundledDefaultsPack.VAULT_HUNTERS, BundledDefaultsPack.select(Set.of("the_vault")::contains));
        assertEquals(BundledDefaultsPack.DEFAULT, BundledDefaultsPack.select(Set.of("quark")::contains));
    }

    @Test
    void woldsVaultsServerGetsTheWoldsRegistryIslandAndHubLayout() throws IOException {
        Files.createDirectories(configFolder.resolve(SkyblockAddonCore.MOD_ID));

        ResourceManager.commonSetup(configFolder, BundledDefaultsPack.WOLDS_VAULTS);

        assertGeneratedFrom("registries/PermissionRegistry.json", "registries/permissions/woldsvaults.json");
        assertGeneratedFrom(ResourceManager.ISLAND_TEMPLATE_FILE, "packs/woldsvaults/structures/island.nbt");
        assertGeneratedFrom("guis/overview.json", "packs/woldsvaults/guis/overview.json");
        assertGeneratedFrom("guis/permissions.json", "packs/woldsvaults/guis/permissions.json");
        assertGeneratedFrom("guis/travel.json", "guis/travel.json");
        assertGeneratedFrom("registries/BiomeRegistry.json", "registries/BiomeRegistry.json");
    }

    @Test
    void vaultHuntersServerGetsTheSharedIslandAndHubLayout() throws IOException {
        Files.createDirectories(configFolder.resolve(SkyblockAddonCore.MOD_ID));

        ResourceManager.commonSetup(configFolder, BundledDefaultsPack.VAULT_HUNTERS);

        assertGeneratedFrom("registries/PermissionRegistry.json", "registries/permissions/vaulthunters.json");
        assertGeneratedFrom(ResourceManager.ISLAND_TEMPLATE_FILE, "structures/island.nbt");
        assertGeneratedFrom("guis/overview.json", "guis/overview.json");
        assertGeneratedFrom("guis/permissions.json", "guis/permissions.json");
    }

    @Test
    void serverWithoutVaultHuntersGetsNoVaultHuntersItemInItsGuis() throws IOException {
        Files.createDirectories(configFolder.resolve(SkyblockAddonCore.MOD_ID));

        ResourceManager.commonSetup(configFolder, BundledDefaultsPack.DEFAULT);

        assertGeneratedFrom("registries/PermissionRegistry.json", "registries/permissions/default.json");
        assertGeneratedFrom("guis/permissions.json", "packs/default/guis/permissions.json");
        try (final var guiFiles = Files.list(configFolder.resolve(SkyblockAddonCore.MOD_ID).resolve("guis"))) {
            for (final Path guiFile : guiFiles.toList()) {
                assertFalse(Files.readString(guiFile).contains("the_vault:"), guiFile.getFileName() + " names a Vault Hunters item");
            }
        }
    }

    private void assertGeneratedFrom(final String configFile, final String bundledAsset) throws IOException {
        try (final InputStream bundled = Objects.requireNonNull(
                SkyblockAddonCore.class.getResourceAsStream("/assets/" + SkyblockAddonCore.MOD_ID + "/" + bundledAsset), bundledAsset)) {
            assertArrayEquals(bundled.readAllBytes(), Files.readAllBytes(configFolder.resolve(SkyblockAddonCore.MOD_ID).resolve(configFile)),
                    configFile + " should be a copy of " + bundledAsset);
        }
    }
}
