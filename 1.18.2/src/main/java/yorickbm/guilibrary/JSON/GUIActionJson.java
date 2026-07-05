package yorickbm.guilibrary.JSON;

import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import yorickbm.guilibrary.GUIItem;
import yorickbm.guilibrary.interfaces.GuiClickItemEvent;
import yorickbm.guilibrary.interfaces.ServerInterface;
import yorickbm.guilibrary.util.JSON.JSONSerializable;
import yorickbm.skyblockaddon.components.ItemStackComponent;

import java.util.HashMap;

public class GUIActionJson implements JSONSerializable {
    private String onClick = "";
    private String onSecondClick = "";
    private HashMap<String, String> data = new HashMap<>();

    public boolean hasPrimary() {
        return !this.onClick.isEmpty();
    }

    public boolean hasSecondary() {
        return !this.onSecondClick.isEmpty();
    }

    public Class<? extends GuiClickItemEvent> getPrimary() {
        return resolveActionClass(this.onClick, "primary");
    }

    public Class<? extends GuiClickItemEvent> getSecondary() {
        return resolveActionClass(this.onSecondClick, "secondary");
    }

    private Class<? extends GuiClickItemEvent> resolveActionClass(
            final String className,
            final String actionName
    ) {
        try {
            final Class<? extends GuiClickItemEvent> actionClass = Class.forName(
                    className,
                    false,
                    GUIActionJson.class.getClassLoader()
            )
                    .asSubclass(GuiClickItemEvent.class);
            actionClass.getConstructor(
                    ServerInterface.class,
                    ServerPlayer.class,
                    Slot.class,
                    GUIItem.class
            );
            return actionClass;
        } catch (final ClassNotFoundException exception) {
            throw new IllegalArgumentException("GUI " + actionName + " action class not found: " + className, exception);
        } catch (final NoSuchMethodException exception) {
            throw new IllegalArgumentException("GUI " + actionName + " action lacks the required constructor: "
                    + className, exception);
        } catch (final ClassCastException exception) {
            throw new IllegalArgumentException("GUI " + actionName + " action does not implement GuiClickItemEvent: "
                    + className, exception);
        }
    }

    public void validate() {
        if (hasPrimary()) getPrimary();
        if (hasSecondary()) getSecondary();
    }

    public ItemStackComponent getData() {
        final ItemStackComponent component = new ItemStackComponent();
        this.data.forEach(component::put);
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
        final GUIActionJson temp = gson.fromJson(json, GUIActionJson.class);

        if(temp.onClick != null) this.onClick = temp.onClick;
        if(temp.onSecondClick != null) this.onSecondClick = temp.onSecondClick;
        if(temp.data != null) this.data = temp.data;
    }
}
