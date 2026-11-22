package yorickbm.skyblockaddon.enhanced;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Opens the island list screen behind a list gui id; the separate transfer list is now the members screen's Make owner. */
public final class ScrollableListGui {

    private ScrollableListGui() {}

    public static void open(ServerPlayer player, CompoundTag data, String variant) {
        switch (variant) {
            case "travel" -> TravelListGui.open(player, data);
            case "members", "transfer" -> PlayerListGui.openMembers(player, data);
            case "visitors" -> PlayerListGui.openVisitors(player, data);
            case "bans" -> PlayerListGui.openBans(player, data);
            case "biomes" -> BiomeListGui.open(player, data);
            case "groups" -> GroupListGui.open(player, data);
            case "set_group" -> GroupAssignGui.openSetGroup(player, data);
            case "members_group" -> GroupAssignGui.openGroupPlayers(player, data);
            default -> throw new IllegalArgumentException("Unknown island list screen: " + variant);
        }
    }
}
