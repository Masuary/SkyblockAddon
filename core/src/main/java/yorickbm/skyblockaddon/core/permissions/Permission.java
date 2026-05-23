package yorickbm.skyblockaddon.core.permissions;

import com.google.gson.Gson;
import yorickbm.skyblockaddon.core.JSON.PermissionDataJson;
import yorickbm.skyblockaddon.core.JSON.ItemStackJson;
import yorickbm.skyblockaddon.core.util.JSON.JSONSerializable;

import java.util.Arrays;

public class Permission implements JSONSerializable {

    protected String id;
    protected ItemStackJson item;
    protected String category;
    protected String[] triggers;
    protected PermissionDataJson data;

    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final Permission temp = gson.fromJson(json, Permission.class);

        this.id = temp.id;
        this.item = temp.item;
        this.category = temp.category;
        this.triggers = temp.triggers;
        this.data = temp.data;
    }

    public String getCategory() {
        return category;
    }

    public String getId() {
        return id;
    }

    public ItemStackJson getItem() { return item; }

    public boolean hasTrigger(final String trigger) {
        return Arrays.stream(triggers).toList().contains(trigger);
    }

    public PermissionDataJson getData() { return this.data; }
}
