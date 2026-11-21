package yorickbm.skyblockaddon.enhanced;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;

/** Opens the permissions screen of a group on its first category. */
public final class PermissionCategoriesGui {

    private PermissionCategoriesGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains(GuiDataKeys.ISLAND_ID) || !data.contains(GuiDataKeys.GROUP_ID)) return;
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;
        IslandGroup group = island.getGroup(data.getUUID(GuiDataKeys.GROUP_ID));
        if (group == null) return;
        PermissionTogglesGui.open(player, data, PermissionTogglesGui.categoriesFor(player).get(0)[0], group);
    }
}
