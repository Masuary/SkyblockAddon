package yorickbm.guilibrary.events;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import yorickbm.guilibrary.GUIItem;
import yorickbm.guilibrary.GUIItemStackHolder;
import yorickbm.guilibrary.GUIPlaceholder;
import yorickbm.guilibrary.interfaces.ServerInterface;

@Cancelable
public class GuiDrawItemEvent extends Event {
    protected ServerInterface instance;

    private final int slot;
    private final GUIItemStackHolder item;
    private final GUIItem itemHolder;

    public GuiDrawItemEvent(final ServerInterface instance, final GUIItem holder) {
        this.instance = instance;
        this.itemHolder = holder;

        this.slot = holder.getSlot();
        this.item = holder.getItemHolder().clone();
    }

    public int getSlot() {
        return this.slot;
    }

    public ServerInterface getHolder() {
        return this.instance;
    }
    public GUIItem getItemHolder() {  return this.itemHolder; }
    public GUIItemStackHolder getItemStackHolder() { return this.item; }

    public void drawItem() {
        final GUIPlaceholder guiItem = new GUIPlaceholder(this.getItemHolder());
        if(guiItem.isClickable()) this.instance.addItem(this.slot, guiItem);

        this.instance.setItem(this.slot, 0, this.item.getItemStack());
    }
}
