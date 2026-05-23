package yorickbm.guilibrary.JSON;

import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.interfaces.GuiClickItemEvent;
import yorickbm.guilibrary.util.JSON.JSONSerializable;

import java.util.HashMap;

public class GUIActionJson implements JSONSerializable {
    private static final Logger LOGGER = LogManager.getLogger();

    private String onClick = "";
    private String onSecondClick = "";
    private HashMap<String, String> data = new HashMap<>();

    public boolean hasPrimary() {
        return !this.onClick.isEmpty();
    }

    public boolean hasSecondary() {
        return !this.onSecondClick.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public Class<? extends GuiClickItemEvent> getPrimary() {
        try {
            // Dynamically load the class by its fully qualified name
            final Class<?> clazz = Class.forName(this.onClick);
            if (GuiClickItemEvent.class.isAssignableFrom(clazz)) {
                return (Class<? extends GuiClickItemEvent>) clazz;
            }
            else {
                LOGGER.error(String.format("Class '%s' does not implement GuiClickItemEvent, primary click removed.", this.onClick));
                this.onClick = ""; //Invalidate trigger
            }
        } catch(final Exception ex) {
            LOGGER.error(String.format("Class '%s' is not found, primary click removed.", this.onClick));
            this.onClick = ""; //Invalidate trigger
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends GuiClickItemEvent> getSecondary() {
        try {
            // Dynamically load the class by its fully qualified name
            final Class<?> clazz = Class.forName(this.onSecondClick);
            if (GuiClickItemEvent.class.isAssignableFrom(clazz)) {
                return (Class<? extends GuiClickItemEvent>) clazz;
            }
            else {
                LOGGER.error(String.format("Class '%s' does not implement GuiClickItemEvent, primary click removed.", this.onSecondClick));
                this.onSecondClick = ""; //Invalidate trigger
            }
        } catch(final Exception ex) {
            LOGGER.error(String.format("Class '%s' is not found, primary click removed.", this.onSecondClick));
            this.onSecondClick = ""; //Invalidate trigger
        }

        return null;
    }

    public CompoundTag getData() {
        final CompoundTag tag = new CompoundTag();

        if(!data.isEmpty()) {
            this.data.forEach(tag::putString);
        }

        return tag;
    }

    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final GUIActionJson temp = gson.fromJson(json, GUIActionJson.class);

        if(temp.onClick != null) this.onClick = temp.onClick;
        if(temp.onSecondClick != null) this.onSecondClick = temp.onSecondClick;
        if(temp.data != null) this.data = temp.data;
    }
}
