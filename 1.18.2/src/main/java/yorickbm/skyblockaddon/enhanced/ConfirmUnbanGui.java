package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.data.KeyValueRow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ConfirmUnbanGui {

    private ConfirmUnbanGui() {}

    public static void open(ServerPlayer player, CompoundTag data, Island island, UUID bannedPlayerId) {
        String bannedPlayerName = PlayerNames.nameOf(Objects.requireNonNull(player.getServer()), bannedPlayerId);
        EnhancedDialog.open(player, "confirm_unban", "Lift ban", EnhancedGuiHelper.getPlayerHead(bannedPlayerId),
                "Let " + bannedPlayerName + " visit again?",
                List.of(KeyValueRow.of("Player", bannedPlayerName, EnhancedDialog.VALUE_WHITE),
                        KeyValueRow.of("After", "can visit again", EnhancedDialog.VALUE_GREEN),
                        KeyValueRow.of("Travel list", "only while public", EnhancedDialog.VALUE_WHITE)),
                "Lift ban", ButtonStyle.PRIMARY,
                p -> {
                    VisitorActionGui.report(p, IslandActionService.unbanPlayer(p, island, bannedPlayerId), "commands.unban.success", bannedPlayerName);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:bans", data);
                },
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:bans", data));
    }
}
