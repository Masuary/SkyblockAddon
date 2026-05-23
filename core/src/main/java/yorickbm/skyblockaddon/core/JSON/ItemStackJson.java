package yorickbm.skyblockaddon.core.JSON;

import com.google.gson.Gson;
import yorickbm.skyblockaddon.core.util.JSON.JSONSerializable;

public class ItemStackJson implements JSONSerializable {

    protected String[] display_name;
    protected String item;
    protected String[][] lore;

    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final ItemStackJson temp = gson.fromJson(json, ItemStackJson.class);

        this.display_name = temp.display_name;
        this.item = temp.item;
        this.lore = temp.lore;
    }

    public String getItem() {
        return item;
    }

    public String[] getDisplay_name() {
        return display_name;
    }

    public String[][] getLore() {
        return lore;
    }
}
