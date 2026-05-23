package yorickbm.skyblockaddon.core.JSON;

import com.google.gson.Gson;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.util.JSON.JSONSerializable;

import java.util.ArrayList;
import java.util.List;

public class PermissionJson implements JSONSerializable {
    public List<Permission> permissions;

    public PermissionJson() {
        this.permissions = new ArrayList<>();
    }


    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final PermissionJson temp = gson.fromJson(json, PermissionJson.class);

        this.permissions = temp.permissions;
    }
}
