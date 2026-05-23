package yorickbm.guilibrary.interfaces;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import yorickbm.guilibrary.GUIItem;

@Cancelable
public class GuiClickItemEvent extends Event {

    protected final ServerInterface instance;
    protected final ServerPlayer target;
    protected final Slot slot;
    protected final GUIItem guiItem;

    public GuiClickItemEvent(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem guiItem) {
        this.instance = instance;
        this.target = player;
        this.slot = slot;

        this.guiItem = guiItem;
    }

    public ServerInterface getHolder() { return this.instance; }
    public ServerPlayer getTarget() { return this.target; }

    public ItemStack getClickedItem() { return this.slot.getItem(); }

}
