package yorickbm.guilibrary.JSON;

import com.google.gson.Gson;
import yorickbm.guilibrary.GUIFiller;
import yorickbm.guilibrary.events.GuiDrawFillerEvent;
import yorickbm.guilibrary.interfaces.ServerInterface;
import yorickbm.guilibrary.util.FillerPattern;
import yorickbm.guilibrary.util.JSON.JSONSerializable;

import java.util.ArrayList;
import java.util.List;

public class GUIFillerJson implements JSONSerializable {
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
            final Class<? extends GuiDrawFillerEvent> eventClass = Class.forName(
                    this.event,
                    false,
                    GUIFillerJson.class.getClassLoader()
            )
                    .asSubclass(GuiDrawFillerEvent.class);
            eventClass.getConstructor(ServerInterface.class, GUIFiller.class, int.class);
            return eventClass;
        } catch (final ClassNotFoundException exception) {
            throw new IllegalArgumentException("GUI filler event class not found: " + this.event, exception);
        } catch (final NoSuchMethodException exception) {
            throw new IllegalArgumentException("GUI filler event lacks the required constructor: "
                    + this.event, exception);
        } catch (final ClassCastException exception) {
            throw new IllegalArgumentException("GUI filler event does not extend GuiDrawFillerEvent: "
                    + this.event, exception);
        }
    }

    public void validate() {
        if (item == null) throw new IllegalArgumentException("GUI filler has no item data");
        action.validate();
        getEvent();
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
