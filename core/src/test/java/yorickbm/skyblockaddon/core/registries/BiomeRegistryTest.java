package yorickbm.skyblockaddon.core.registries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.util.DataComponent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiomeRegistryTest {
    @Test
    void hidesConfiguredBiomesThatAreNotInTheLiveRegistry(@TempDir final Path configDirectory)
            throws Exception {
        final Path registryDirectory = Files.createDirectories(
                configDirectory.resolve(SkyblockAddonCore.MOD_ID).resolve("registries")
        );
        Files.writeString(registryDirectory.resolve("BiomeRegistry.json"), """
                {
                  "biomes": {
                    "minecraft:plains": "minecraft:grass_block",
                    "regions_unexplored:ashen_woodland": "regions_unexplored:ashen_log"
                  }
                }
                """);

        final BiomeRegistry registry = new BiomeRegistry(
                configDirectory,
                List.of("minecraft:plains")
        );
        final DataComponent component = new DataComponent() { };
        registry.getNextData(component);

        assertEquals(1, registry.getSize());
        assertEquals("minecraft:plains", component.getObject("biome", String.class));
    }
}
