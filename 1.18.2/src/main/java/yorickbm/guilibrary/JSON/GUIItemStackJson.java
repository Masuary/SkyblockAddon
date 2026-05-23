package yorickbm.guilibrary.JSON;

import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUIItemStackHolder;
import yorickbm.guilibrary.util.Helper;
import yorickbm.guilibrary.util.JSON.JSONSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class GUIItemStackJson implements JSONSerializable {

    private List<String> display_name;
    private String item = "minecraft:barrier";
    private int amount = 1;
    private List<List<String>> lore = new ArrayList<>();
    private HashMap<String, String> data = new HashMap<>();

    public GUIItemStackHolder getItemStackHolder() {
        return new GUIItemStackHolder(Helper.getItem(item.toLowerCase(), Items.BARRIER), this.amount, getCompoundTag(), display_name, lore);
    }

    /**
     * Set itemstack data
     */
    public CompoundTag getCompoundTag() {
        final CompoundTag tag = new CompoundTag();

        if(!data.isEmpty()) {
            this.data.forEach(tag::putString);
        }

        return tag;
    }

    /**
     * Get TextComponent Display name for Item.
     *
     * @return - TextComponent
     */
    public TextComponent getDisplayName() throws NullPointerException {
        final TextComponent component = new TextComponent("");

        for(final String string : this.display_name) {
            final Component deserialized = Component.Serializer.fromJson(string);
            component.append(Objects.requireNonNull(deserialized));
        }

        return component;
    }

    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final GUIItemStackJson temp = gson.fromJson(json, GUIItemStackJson.class);
        this.display_name = temp.display_name;
        if(temp.item != null) this.item = temp.item;
        if(temp.amount < 1) this.amount = temp.amount;
        if(temp.lore != null) this.lore = temp.lore;
        if(temp.data != null) this.data = temp.data;
    }

}
