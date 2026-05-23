package yorickbm.guilibrary.events;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import yorickbm.guilibrary.GUIFiller;
import yorickbm.guilibrary.GUIItemStackHolder;
import yorickbm.guilibrary.GUIPlaceholder;
import yorickbm.guilibrary.interfaces.ServerInterface;
import yorickbm.guilibrary.util.FillerPattern;

@Cancelable
public class GuiDrawFillerEvent extends Event {
    protected ServerInterface instance;

    private final GUIItemStackHolder item;
    private int slots = 0;
    private FillerPattern pattern;
    private final GUIFiller filler;

    public GuiDrawFillerEvent(final ServerInterface instance, final GUIFiller filler, final int slots) {
        this.instance = instance;
        this.item = filler.getItemHolder().clone();
        this.pattern = filler.getPattern();
        this.slots = slots;
        this.filler = filler;
    }

    public GUIItemStackHolder getItemStackHolder() { return this.item; }

    public FillerPattern getPattern() {
        return this.pattern;
    }
    public void setPattern(final FillerPattern pattern) {
        this.pattern = pattern;
    }

    public void drawItem(final int slot, final ItemStack item) {
        final GUIPlaceholder guiItem = new GUIPlaceholder(this.filler);
        if(guiItem.isClickable()) this.instance.addItem(slot, guiItem);

        this.instance.setItem(slot, 0, item);
    }

    public int getSlots() { return this.slots; }

    public boolean slotIsEmpty(final int slot) {
        return !this.instance.getSlot(slot).hasItem();
    }

    public void setMaxPage(final int page) {
        this.instance.setMaxPage(page);
    }
    public int getCurrentPage() {
        return this.instance.getCurrentPage();
    }
}
