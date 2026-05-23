package yorickbm.skyblockaddon.core.JSON;

import com.google.gson.Gson;
import yorickbm.skyblockaddon.core.util.JSON.JSONSerializable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    public List<String> getSkyblockaddonData() { return Arrays.stream(this.skyblockaddon).collect(Collectors.toList()); }
    public List<String> getItemsData() { return Arrays.stream(this.items).collect(Collectors.toList()); }
    public List<String> getBlocksData() { return Arrays.stream(this.blocks).collect(Collectors.toList()); }
    public List<String> getEntitiesData() { return Arrays.stream(this.entities).collect(Collectors.toList()); }

    public boolean getDefault() {
        return this.defaults;
    }
}
