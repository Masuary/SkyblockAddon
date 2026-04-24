package yorickbm.skyblockaddon.core.registries;

import yorickbm.skyblockaddon.core.JSON.BiomeRegistryJson;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.registries.interfaces.ComponentObjectCoupling;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;
import yorickbm.skyblockaddon.core.util.JSON.JSONEncoder;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class BiomeRegistry extends SkyblockAddonRegistry<DataComponent> implements ComponentObjectCoupling<String> {
    private Map<String, String> biomes;
    private List<Map.Entry<String, String>> entries;

    public BiomeRegistry(Path FMLPath, String defaultItem, List<String> forgeBiomes) {
        final Set<String> availableBiomes = new HashSet<>(forgeBiomes);
        this.biomes = new LinkedHashMap<>();
        try {
            final BiomeRegistryJson data = JSONEncoder.loadFromFile(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/BiomeRegistry.json"), BiomeRegistryJson.class);
            data.toMap().forEach((biomeId, iconItem) -> {
                if (availableBiomes.contains(biomeId)) this.biomes.put(biomeId, iconItem);
            });
        } catch (final Exception e) {
            //JSON missing or unreadable - fall back to default icon for every registered biome
            forgeBiomes.forEach(v -> this.biomes.put(v, defaultItem));
        }
        this.entries = new ArrayList<>(this.biomes.entrySet());
    }

    @Override
    public void getNextData(DataComponent component) {
        final Map.Entry<String, String> entry = this.entries.get(this.index);

        component.put("biome", entry.getKey());
        component.put("name", Arrays.stream(
                        entry.getKey()
                                .split(":")[1]
                                .replace("_", " ")
                                .split(" ")
                )
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" ")));
        index++;
    }

    @Override
    public int getSize() {
        return this.entries.size();
    }

    public Optional<String> getDataForComponent(DataComponent component) {
        if(!component.contains("biome")) return Optional.empty();

        return Optional.ofNullable(
                this.biomes.get(component.getObject("biome", String.class))
        );
    }
}
