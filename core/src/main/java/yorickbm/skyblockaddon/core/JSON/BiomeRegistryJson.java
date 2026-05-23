package yorickbm.skyblockaddon.core.JSON;

import com.google.gson.Gson;
import yorickbm.skyblockaddon.core.util.JSON.JSONSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

public class BiomeRegistryJson implements JSONSerializable {
    LinkedHashMap<String, String> biomes;

    @Override
    public String toJSON() {
        return "";
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final BiomeRegistryJson temp = gson.fromJson(json, BiomeRegistryJson.class);
        this.biomes = temp.biomes;
    }

    public Map<String, String> toMap() {
        final Map<String, String> data = new LinkedHashMap<>();

        this.biomes.forEach((s,v) -> {
            data.put(s.toLowerCase(), v.toLowerCase());
        });

        return data;
    }
}
