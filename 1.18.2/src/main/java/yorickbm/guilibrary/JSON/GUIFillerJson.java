package yorickbm.guilibrary.JSON;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.GUIFiller;
import yorickbm.guilibrary.events.GuiDrawFillerEvent;
import yorickbm.guilibrary.util.FillerPattern;
import yorickbm.guilibrary.util.JSON.JSONSerializable;

import java.util.ArrayList;
import java.util.List;

public class GUIFillerJson implements JSONSerializable {
    private static final Logger LOGGER = LogManager.getLogger();

    private GUIItemStackJson item;
    private FillerPattern pattern;
    private GUIActionJson action = new GUIActionJson();
    private List<String> conditions = new ArrayList<>();
    private String event = "";

    /*
    Get GUI Item for slot
     */
    public GUIFiller getItem() {

        final GUIFiller.Builder builder = new GUIFiller.Builder()
                .setPattern(this.pattern)
                .setItemStack(this.item.getItemStackHolder())
                .setConditions(this.conditions)
                .setEvent(this.getEvent());

        if(this.action.hasPrimary()) builder.setPrimaryClickClass(this.action.getPrimary());
        if(this.action.hasSecondary()) builder.setSecondaryClickClass(this.action.getSecondary());

        builder.setActionData(this.action.getData());

        return builder.build();
    }

    public Class<? extends GuiDrawFillerEvent> getEvent() {
        if(this.event.isEmpty()) return null;

        try {
            // Dynamically load the class by its fully qualified name
            final Class<?> clazz = Class.forName(this.event);
            if (GuiDrawFillerEvent.class.isAssignableFrom(clazz)) {
                return (Class<? extends GuiDrawFillerEvent>) clazz;
            }
            else {
                LOGGER.error(String.format("Class '%s' does not implement GuiDrawFillerEvent, event removed.", this.event));
                this.event = ""; //Invalidate trigger
            }
        } catch(final Exception ex) {
            LOGGER.error(String.format("Class '%s' is not found, event removed.", this.event));
            this.event = ""; //Invalidate trigger
        }
        return null;
    }

    @Override
    public String toJSON() {
        final Gson gson = new Gson();
        return gson.toJson(this);
    }

    @Override
    public void fromJSON(final String json) {
        final Gson gson = new Gson();
        final GUIFillerJson temp = gson.fromJson(json, GUIFillerJson.class);

        this.pattern = temp.pattern;
        this.item = temp.item;
        if(temp.event != null) this.event = temp.event;
        if(temp.action != null) this.action = temp.action;
        if(temp.conditions != null) this.conditions = temp.conditions;
    }
}
