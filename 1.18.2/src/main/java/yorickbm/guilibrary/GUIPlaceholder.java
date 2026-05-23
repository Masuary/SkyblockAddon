package yorickbm.guilibrary;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.eventbus.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.interfaces.GuiClickItemEvent;
import yorickbm.guilibrary.interfaces.ServerInterface;

public class GUIPlaceholder {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Class<? extends GuiClickItemEvent> primaryClickClass, secondaryClickClass;
    private final GUIItem guiItem;

    public GUIPlaceholder(final GUIItem guiItem) {
        this.primaryClickClass = guiItem.getPrimaryClickClass();
        this.secondaryClickClass = guiItem.getSecondaryClickClass();

        this.guiItem = guiItem;
    }

    public Event getPrimaryClick(final ServerInterface instance, final ServerPlayer player, final Slot slot) {
        if(primaryClickClass == null) return new Event();
        return createEventInstance(primaryClickClass, instance, player, slot);
    }

    public Event getSecondaryClick(final ServerInterface instance, final ServerPlayer player, final Slot slot) {
        if(secondaryClickClass == null) return new Event();
        return createEventInstance(secondaryClickClass, instance, player, slot);
    }

    // Helper method to create a new event instance using reflection
    private Event createEventInstance(final Class<? extends GuiClickItemEvent> eventClass, final ServerInterface instance, final ServerPlayer player, final Slot slot) {
        try {
            // Create a new instance of the event class, passing the parameters to its constructor
            return eventClass.getConstructor(ServerInterface.class, ServerPlayer.class, Slot.class, GUIItem.class)
                    .newInstance(instance, player, slot, this.guiItem);  // Pass the arguments
        } catch (final Exception e) {
            LOGGER.error("Failure on converting {} into an GuiClickItemEvent...\n{}", eventClass.getSimpleName(), e);
            return new Event();  // Return null if event creation fails
        }
    }

    public boolean isClickable() {
        return this.primaryClickClass != null || this.secondaryClickClass != null;
    }
}
