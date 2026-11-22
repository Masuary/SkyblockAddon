package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.data.KeyValueRow;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;
import yorickbm.skyblockaddon.islands.IslandActionService;

import java.util.List;

public final class ConfirmRemoveGroupGui {

    private ConfirmRemoveGroupGui() {}

    public static void open(ServerPlayer player, CompoundTag data, Island island, ForgeIslandGroup group) {
        EnhancedDialog.open(player, "confirm_remove_group", "Remove group", group.getItem(),
                "Remove group " + group.getName() + "?",
                List.of(KeyValueRow.of("Players", String.valueOf(group.getMembers().size()), EnhancedDialog.VALUE_WHITE),
                        KeyValueRow.of("They move to", "Members or Visitors", EnhancedDialog.VALUE_AQUA),
                        KeyValueRow.of("Undo", "not possible", EnhancedDialog.VALUE_RED)),
                "Remove", ButtonStyle.DANGER,
                p -> {
                    IslandActionResult result = IslandActionService.removeGroup(p, island, group.getId());
                    if (result.succeeded()) {
                        p.sendMessage(new TextComponent("Group '" + group.getName() + "' removed.").withStyle(ChatFormatting.RED), p.getUUID());
                    } else {
                        IslandActionService.sendFailure(p, result, group.getName());
                    }
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                },
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data));
    }
}
