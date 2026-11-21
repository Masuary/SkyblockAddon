package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
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

/** A visitor on the island: send them to spawn, or ban them (which also sends them to spawn). */
public final class VisitorActionGui {

    private VisitorActionGui() {}

    public static void open(ServerPlayer player, CompoundTag data, ForgeIsland island, UUID visitorId) {
        String visitorName = PlayerNames.nameOf(Objects.requireNonNull(player.getServer()), visitorId);
        EnhancedDialog.openWithActions(player, "visitor_action", visitorName, EnhancedGuiHelper.getPlayerHead(visitorId),
                List.of(new TextComponent(visitorName + " is visiting your island."),
                        new TextComponent("Expel sends them to spawn.").withStyle(ChatFormatting.GRAY),
                        new TextComponent("Ban also keeps them off the island.").withStyle(ChatFormatting.GRAY)),
                List.of(new EnhancedDialog.Action("Expel", ButtonStyle.PRIMARY, p -> expel(p, data, island, visitorId, visitorName)),
                        new EnhancedDialog.Action("Ban", ButtonStyle.DANGER, p -> ban(p, data, island, visitorId, visitorName))),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:visitors", data));
    }

    private static void expel(ServerPlayer player, CompoundTag data, ForgeIsland island, UUID visitorId, String visitorName) {
        ServerPlayer visitor = Objects.requireNonNull(player.getServer()).getPlayerList().getPlayer(visitorId);
        IslandActionResult result = visitor == null
                ? IslandActionResult.TARGET_NOT_ON_ISLAND
                : IslandActionService.expelVisitor(player, island, visitor);
        report(player, result, "commands.expel.success", visitorName);
        GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:visitors", data);
    }

    private static void ban(ServerPlayer player, CompoundTag data, ForgeIsland island, UUID visitorId, String visitorName) {
        report(player, IslandActionService.banPlayer(player, island, visitorId), "commands.ban.success", visitorName);
        GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:visitors", data);
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
