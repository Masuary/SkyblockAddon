package yorickbm.guilibrary.interfaces;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import yorickbm.guilibrary.GUIFiller;
import yorickbm.guilibrary.GUIItem;
import yorickbm.guilibrary.GUIPlaceholder;
import yorickbm.guilibrary.GUIType;
import yorickbm.guilibrary.events.GuiDrawFillerEvent;
import yorickbm.guilibrary.events.GuiDrawItemEvent;
import yorickbm.guilibrary.util.FillerPattern;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ServerInterface extends AbstractContainerMenu {
    protected CompoundTag data;
    protected final SimpleContainer container;
    protected Player holder;
    protected GUIType type;

    private final Map<Integer, GUIPlaceholder> items;

    private int maxPage = 1;
    private int currPage = 1;

    public ServerInterface(final int syncId, final Inventory playerInventory, final Player holder, final GUIType type, final CompoundTag data) {
        super(fromRows(type.getRows()), syncId);
        this.container = new SimpleContainer(type.getRows() * 9);
        this.holder = holder;

        this.type = type;
        this.items = new HashMap<>();
        this.data = data;

        final int i = (type.getRows() - 4) * 18;
        int n, m;

        //Register our inventory slots
        for (n = 0; n < type.getRows(); ++n) {
            for (m = 0; m < 9; ++m) {
                this.addSlot(new Slot(this.container, m + n * 9, 8 + m * 18, 18 + n * 18) {
                    @Override
                    public boolean mayPlace(@NotNull final ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(@NotNull final Player playerEntity) {
                        return false;
                    }
                });
            }
        }

        // Register players inventory (Non toolbar slots)
        for (n = 0; n < 3; ++n) {
            for (m = 0; m < 9; ++m) {
                addSlot(new Slot(playerInventory, m + n * 9 + 9, 8 + m * 18, 103 + n * 18 + i) {
                    @Override
                    public boolean mayPlace(@NotNull final ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(@NotNull final Player playerEntity) {
                        return false;
                    }
                });
            }
        }

        // Register players toolbar slots
        for (n = 0; n < 9; ++n) {
            this.addSlot(new Slot(playerInventory, n, 8 + n * 18, 161 + i) {
                @Override
                public boolean mayPlace(@NotNull final ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(@NotNull final Player playerEntity) {
                    return false;
                }
            });
        }

        this.update();
    }

    @Override
    public boolean stillValid(@NotNull final Player player) {
        return true;
    }

    /**
     * Returns the player entity that owns the creation of the GUI instance
     * @return - Player Entity
     */
    public Player getOwner() { return this.holder; }

    /**
     * Close container for the source entity.
     * Source Entity - Who opened the GUI
     */
    public void close() {
        this.holder.closeContainer();
    }

    /**
     * Get container responsible for instance.
     */
    public SimpleContainer getContainer() {
        return this.container;
    }

    /**
     * Return container NBT data.
     */
    public CompoundTag getData() {
        return this.data;
    }

    /**
     * Event triggers upon clicking an items within the GUI
     */
    @Override
    public void clicked(final int slotIndex, final int buttonId, @NotNull final ClickType actionType, @NotNull final Player playerEntity) {
        if (slotIndex < 0)
            return;

        final Slot slot = this.slots.get(slotIndex);
        if(slot.hasItem()) {
            final Optional<GUIPlaceholder> placeholder = Optional.ofNullable(this.items.get(slot.getSlotIndex()));
            placeholder.ifPresent(gi -> {
                switch (buttonId) {
                    case 0 -> MinecraftForge.EVENT_BUS.post(gi.getPrimaryClick(this, (ServerPlayer) playerEntity, slot));
                    case 1 -> MinecraftForge.EVENT_BUS.post(gi.getSecondaryClick(this, (ServerPlayer) playerEntity, slot));
                }
            });
        }

        this.broadcastChanges();
    }

    /**
     * Convert row to Menu Type
     *
     * @param rows - Rows wished in menu
     * @return MenuType for rows
     */
    private static MenuType<ChestMenu> fromRows(final int rows) {
        return switch (rows) {
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x1;
        };
    }

    /**
     * Initiates a draw of the inventory
     */
    public void update() {
        this.container.clearContent(); //Clear Inventory

        //Add fillers
        for(final GUIFiller item : type.getFillers().stream().filter(f -> f.getPattern() != FillerPattern.EMPTY).toList()) {
            if(item.hasEvent()) MinecraftForge.EVENT_BUS.post(item.getEvent(this, type.getRows() * 9));
            else MinecraftForge.EVENT_BUS.post(new GuiDrawFillerEvent(this, item, type.getRows() * 9));
        }

        //Add items
        for(final GUIItem item : type.getItems()) {
            MinecraftForge.EVENT_BUS.post(new GuiDrawItemEvent(this, item));
        }

        //Add fillers
        for(final GUIFiller item : type.getFillers().stream().filter(f -> f.getPattern() == FillerPattern.EMPTY).toList()) {
            if(item.hasEvent()) MinecraftForge.EVENT_BUS.post(item.getEvent(this, type.getRows() * 9));
            else MinecraftForge.EVENT_BUS.post(new GuiDrawFillerEvent(this, item, type.getRows() * 9));
        }
    }

    /**
     * Remove item from data set if its not rendered (prevents action triggers)
     */
    public void removeItem(final int slot) {
        this.items.remove(slot);
    }

    /**
     * Add item into data set to add action trigger for item
     * @param item
     */
    public void addItem(final int slot, final GUIPlaceholder item) {
        this.items.put(slot, item);
    }

    public int getCurrentPage() {  return this.currPage; }

    public int getMaxPage() { return this.maxPage; }
    public void setMaxPage(final int page) { this.maxPage = page; }

    public boolean nextPage() {
        if(this.currPage == this.maxPage) return false;
        else this.currPage += 1;
        return true;
    }
    public boolean prevPage() {
        if(this.currPage == 1) return false;
        else this.currPage -= 1;
        return true;
    }
}
