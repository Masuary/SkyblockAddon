package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.data.KeyValueRow;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The visitor card's actions: expel sends the visitor to spawn, ban asks first and also keeps them off the island. */
final class VisitorActionGui {

    private VisitorActionGui() {
    }

    static void expel(ServerPlayer player, CompoundTag data, ForgeIsland island, UUID visitorId, String visitorName) {
        ServerPlayer visitor = Objects.requireNonNull(player.getServer()).getPlayerList().getPlayer(visitorId);
        IslandActionResult result = visitor == null
                ? IslandActionResult.TARGET_NOT_ON_ISLAND
                : IslandActionService.expelVisitor(player, island, visitor);
        report(player, result, "commands.expel.success", visitorName);
        GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:visitors", data);
    }

    static void openBanConfirm(ServerPlayer player, CompoundTag data, ForgeIsland island, UUID visitorId) {
        String visitorName = PlayerNames.nameOf(Objects.requireNonNull(player.getServer()), visitorId);
        EnhancedDialog.open(player, "confirm_ban", "Ban player", EnhancedGuiHelper.getPlayerHead(visitorId),
                "Ban " + visitorName + " from the island?",
                List.of(KeyValueRow.of("Now", "sent to spawn", EnhancedDialog.VALUE_WHITE),
                        KeyValueRow.of("Travel here", "blocked", EnhancedDialog.VALUE_RED),
                        KeyValueRow.of("Undo", "Lift ban in Bans", EnhancedDialog.VALUE_WHITE)),
                "Ban", ButtonStyle.DANGER,
                p -> {
                    report(p, IslandActionService.banPlayer(p, island, visitorId), "commands.ban.success", visitorName);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:visitors", data);
                },
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:visitors", data));
    }

    static void report(ServerPlayer player, IslandActionResult result, String successKey, String subject) {
        if (!result.succeeded()) {
            IslandActionService.sendFailure(player, result, subject);
            return;
        }
        player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString(successKey).formatted(subject))
                .withStyle(ChatFormatting.GREEN), player.getUUID());
    }
}
