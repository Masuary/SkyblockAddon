package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
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
        EnhancedDialog.open(player, "confirm_unban", "Lift ban?", EnhancedGuiHelper.getPlayerHead(bannedPlayerId),
                List.of(new TextComponent("Let " + bannedPlayerName + " visit again?"),
                        new TextComponent("They can travel to the island again").withStyle(ChatFormatting.GRAY),
                        new TextComponent("while it is public.").withStyle(ChatFormatting.GRAY)),
                "Unban", ButtonStyle.PRIMARY,
                p -> {
                    VisitorActionGui.report(p, IslandActionService.unbanPlayer(p, island, bannedPlayerId), "commands.unban.success", bannedPlayerName);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:bans", data);
                },
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:bans", data));
    }
}
