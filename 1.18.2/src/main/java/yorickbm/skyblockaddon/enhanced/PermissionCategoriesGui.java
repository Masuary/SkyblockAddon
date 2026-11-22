package yorickbm.skyblockaddon.enhanced;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Opens the permissions screen of a group on the category the gui data names, else its first category. */
public final class PermissionCategoriesGui {

    private PermissionCategoriesGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        PermissionTogglesGui.open(player, data);
    }
}
