package yorickbm.guilibrary.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.guilibrary.GUIType;
import yorickbm.guilibrary.interfaces.MenuProviderInterface;
import yorickbm.guilibrary.interfaces.ServerInterface;

import java.util.List;

@Cancelable
public class OpenMenuEvent extends Event {
    private static final Logger LOGGER = LogManager.getLogger();

    protected MenuProviderInterface provider;
    protected ServerPlayer target;
    protected CompoundTag data;
    protected String guiId;

    protected List<TextComponent> title;

    public OpenMenuEvent(final String id, final ServerPlayer target, final CompoundTag data) {
        this.guiId = id;
        final GUIType guiStructure = GUILibraryRegistry.getValue(id);
        if(guiStructure == null) {
            setCanceled(true);
            LOGGER.error(String.format("Failed to open menu '%s'.", id));
            return;
        }

        this.title = guiStructure.getTitle();
        this.data = data;

        this.provider = new MenuProviderInterface() {
            @Override
            public @NotNull Component getDisplayName() {
                return getTitle().stream().reduce(new TextComponent(""), (a, b) -> (TextComponent) a.append(b));
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(final int i, @NotNull final Inventory inventory, @NotNull final Player player) {
                this.container = new ServerInterface(i, inventory, player, guiStructure, data);
                return this.container;
            }
        };
        this.target = target;
    }

    public MenuProviderInterface getProvider() { return this.provider; }
    public ServerPlayer getTarget() { return this.target; }

    public List<TextComponent> getTitle() { return this.title; }
    public void setTitle(final List<TextComponent> title) { this.title = title;}

    public CompoundTag getData() { return this.data; }
    public String getGuiId() { return this.guiId; }

}
