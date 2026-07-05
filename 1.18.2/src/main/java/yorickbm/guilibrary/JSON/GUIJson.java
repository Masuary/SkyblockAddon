package yorickbm.guilibrary.JSON;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    private int rows;
    private List<GUIItemJson> items;
    private List<GUIFillerJson> fillers;

    public GUIJson() {
    }

    public String getKey() {
        return key;
    }

    public List<TextComponent> getTitle() throws NullPointerException {
        final List<TextComponent> result = new ArrayList<>();
        try {
            for (final String raw : title) {
                final TextComponent component = (TextComponent) Objects.requireNonNull(
                        (Component) Component.Serializer.fromJson(raw)
                );
                result.add(component);
            }
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("Invalid GUI title JSON for '" + key + "'", exception);
        }
        return result;
    }

    public int getRows() {
        return rows;
    }

    public List<GUIItem> getItems() {
        return items.stream().map(GUIItemJson::getItem).collect(Collectors.toList());
    }

    public List<GUIFiller> getFillers() {
        return fillers.stream().map(GUIFillerJson::getItem).collect(Collectors.toList());
    }

    public void validateConfiguredClasses() {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("GUI has no key");
        if (items == null || fillers == null) {
            throw new IllegalArgumentException("GUI '" + key + "' must define items and fillers");
        }
        items.forEach(GUIItemJson::validate);
        fillers.forEach(GUIFillerJson::validate);
    }

    public String toJSON() {
        return gson().toJson(this);
    }

    public void fromJSON(String json) {
        Gson gson = gson();
        GUIJson parsed = gson.fromJson(json, GUIJson.class);
        this.key = parsed.key;
        this.title = parsed.title;
        if (parsed.rows != 0) {
            this.rows = parsed.rows;
        }
        if (parsed.items != null) {
            this.items = parsed.items;
        }
        if (parsed.fillers != null) {
            this.fillers = parsed.fillers;
        }
    }

    private static Gson gson() {
        return new GsonBuilder()
                .registerTypeAdapter(LoreLineJson.class, new LoreLineDeserializer())
                .create();
    }
}
