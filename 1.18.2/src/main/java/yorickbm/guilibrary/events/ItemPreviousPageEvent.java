package yorickbm.guilibrary.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.eventbus.api.Cancelable;
import yorickbm.guilibrary.GUIItem;
import yorickbm.guilibrary.interfaces.GuiClickItemEvent;
import yorickbm.guilibrary.interfaces.ServerInterface;

@Cancelable
public class ItemPreviousPageEvent extends GuiClickItemEvent {
    public ItemPreviousPageEvent(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
        super(instance, player, slot, item);
    }
}
