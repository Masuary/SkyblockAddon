package yorickbm.skyblockaddon.core.JSON;

import com.google.gson.Gson;
import yorickbm.skyblockaddon.core.util.JSON.JSONSerializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PermissionDataJson implements JSONSerializable {

    protected String[] skyblockaddon;
    protected String[] items;
    protected String[] blocks;
    protected String[] entities;
    protected boolean defaults = false;

    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final PermissionDataJson temp = gson.fromJson(json, PermissionDataJson.class);

        this.skyblockaddon = temp.skyblockaddon;
        this.items = temp.items;
        this.blocks = temp.blocks;
        this.entities = temp.entities;
        this.defaults = temp.defaults;
    }

    public List<String> getSkyblockaddonData() { return rulesOrEmpty(this.skyblockaddon); }
    public List<String> getItemsData() { return rulesOrEmpty(this.items); }
    public List<String> getBlocksData() { return rulesOrEmpty(this.blocks); }
    public List<String> getEntitiesData() { return rulesOrEmpty(this.entities); }

    private static List<String> rulesOrEmpty(final String[] rules) {
        return rules == null ? new ArrayList<>() : Arrays.stream(rules).collect(Collectors.toList());
    }

    public Stream<String> getItemBlockAndEntityRules() {
        return Stream.of(this.items, this.blocks, this.entities).filter(Objects::nonNull).flatMap(Arrays::stream);
    }

    public boolean getDefault() {
        return this.defaults;
    }
}
