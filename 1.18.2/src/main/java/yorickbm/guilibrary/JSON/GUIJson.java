package yorickbm.guilibrary.JSON;

import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import yorickbm.guilibrary.GUIFiller;
import yorickbm.guilibrary.GUIItem;
import yorickbm.guilibrary.util.JSON.JSONSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GUIJson implements JSONSerializable {

    private String key;
    private List<String> title;
    private int rows = 3;

    private List<GUIItemJson> items;
    private List<GUIFillerJson> fillers;

    /**
     * Get GUI configured key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Convert string formated Text Component for GUI title.
     *
     * @return - TextComponent
     */
    public List<TextComponent> getTitle() throws NullPointerException {
        final List<TextComponent> components = new ArrayList<>();
        try {
            for(final String string : this.title) {
                final Component deserialized = Component.Serializer.fromJson(string);
                components.add((TextComponent) Objects.requireNonNull(deserialized));
            }
        } catch (final Exception ex) {
            components.add((TextComponent) new TextComponent("Invalid JSON in title").withStyle(ChatFormatting.RED));
        }
        return components;
    }

    /**
     * Get amount of rows for GUI.
     */
    public int getRows() {
        return rows;
    }

    /**
     * Get Gui Items
     */
    public List<GUIItem> getItems() {
        return items.stream().map(GUIItemJson::getItem).collect(Collectors.toList());
    }

    /**
     * Get Gui Fillers
     */
    public List<GUIFiller> getFillers() {
        return fillers.stream().map(GUIFillerJson::getItem).collect(Collectors.toList());
    }

    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final GUIJson temp = gson.fromJson(json, GUIJson.class);

        this.key = temp.key;
        this.title = temp.title;
        if(temp.rows != 0) this.rows = temp.rows;
        if(temp.items != null) this.items = temp.items;
        if(temp.fillers != null) this.fillers = temp.fillers;
    }
}
