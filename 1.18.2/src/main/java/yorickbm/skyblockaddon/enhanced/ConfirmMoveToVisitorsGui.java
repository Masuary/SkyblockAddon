package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.data.KeyValueRow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Asks before a member is moved to Visitors, which removes them from the island. */
final class ConfirmMoveToVisitorsGui {

    private ConfirmMoveToVisitorsGui() {
    }

    static void open(ServerPlayer player, CompoundTag data, ForgeIsland island, UUID memberId) {
        String memberName = PlayerNames.nameOf(Objects.requireNonNull(player.getServer()), memberId);
        String currentGroup = island.getGroupForEntityUUID(memberId).map(IslandGroup::getName).orElse("Members");
        EnhancedDialog.open(player, "confirm_move_to_visitors", "Move to Visitors", EnhancedGuiHelper.getPlayerHead(memberId),
                "Remove " + memberName + " from the island?",
                List.of(KeyValueRow.of("Now", currentGroup, EnhancedDialog.VALUE_AQUA),
                        KeyValueRow.of("After", "not a member", EnhancedDialog.VALUE_RED),
                        KeyValueRow.of("Back", "needs a new invite", EnhancedDialog.VALUE_WHITE)),
                "Remove", ButtonStyle.DANGER,
                p -> GroupAssignGui.assign(p, data, memberId, SkyblockAddonCore.VISITORS_GROUP_ID),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:set_group", data));
    }
}
