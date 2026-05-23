package yorickbm.skyblockaddon.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.eventbus.api.Cancelable;
import yorickbm.guilibrary.GUIItem;
import yorickbm.guilibrary.interfaces.GuiClickItemEvent;
import yorickbm.guilibrary.interfaces.ServerInterface;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;

@Cancelable
public class IslandEvents extends GuiClickItemEvent {
    private ForgeIsland island;
    public ForgeIsland getIsland() { return island; }

    public IslandEvents(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
        super(instance, player, slot, item);
        final CompoundTag guiData = instance.getData();

        //If GUI does not contain an IslandId we do not add an island Object
        if(!guiData.contains("island_id")) return;

        final Island island = IslandManager.getInstance().getIslandByUUID(guiData.getUUID("island_id"));

        //Fail action if island not found
        if(island == null) {
            setCanceled(true);
            return;
        }

        this.island = (ForgeIsland) island;
    }

    @Cancelable
    public static class TeleportToIsland extends IslandEvents {
        public TeleportToIsland(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class TravelToIsland extends IslandEvents {
        public TravelToIsland(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class LeaveIsland extends IslandEvents {
        public LeaveIsland(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class SetSpawnPoint extends IslandEvents {
        public SetSpawnPoint(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class ChangeVisibility extends IslandEvents {
        public ChangeVisibility(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class CreateNewGroup extends IslandEvents {
        public CreateNewGroup(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class RemoveGroup extends IslandEvents {
        public RemoveGroup(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class SetGroupPermission extends IslandEvents {
        public SetGroupPermission(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class SetPlayerGroup extends IslandEvents {
        public SetPlayerGroup(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class InviteNewMember extends IslandEvents {
        public InviteNewMember(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class KickMember extends IslandEvents {
        public KickMember(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }

    @Cancelable
    public static class UpdateBiome extends IslandEvents {
        public UpdateBiome(final ServerInterface instance, final ServerPlayer player, final Slot slot, final GUIItem item) {
            super(instance, player, slot, item);
        }
    }
}
