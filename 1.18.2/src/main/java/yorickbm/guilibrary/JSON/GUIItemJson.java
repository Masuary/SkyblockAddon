package yorickbm.guilibrary.JSON;

import com.google.gson.Gson;
import yorickbm.guilibrary.GUIItem;
import yorickbm.guilibrary.util.JSON.JSONSerializable;

import java.util.ArrayList;
import java.util.List;

public class GUIItemJson implements JSONSerializable {

    private GUIItemStackJson item;
    private int slot;
    private GUIActionJson action = new GUIActionJson();
    private List<String> conditions = new ArrayList<>();

    /*
    Get GUI Item for slot
     */
    public GUIItem getItem() {

        final GUIItem.Builder builder = new GUIItem.Builder()
                .setSlot(this.slot)
                .setItemStack(this.item.getItemStackHolder())
                .setConditions(this.conditions);

        if(this.action.hasPrimary()) builder.setPrimaryClickClass(this.action.getPrimary());
        if(this.action.hasSecondary()) builder.setSecondaryClickClass(this.action.getSecondary());

        builder.setActionData(this.action.getData());

        return builder.build();
    }

    public void validate() {
        if (item == null) throw new IllegalArgumentException("GUI item in slot " + slot + " has no item data");
        action.validate();
    }


    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final GUIItemJson temp = gson.fromJson(json, GUIItemJson.class);

        this.slot = temp.slot;
        this.item = temp.item;
        if(temp.action != null) this.action = temp.action;
        if(temp.conditions != null) this.conditions = temp.conditions;
    }
}
