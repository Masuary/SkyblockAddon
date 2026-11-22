package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.data.KeyValueRow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.OwnershipTransferConfirmation;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ConfirmTransferGui {

    private ConfirmTransferGui() {}

    public static void open(ServerPlayer player, CompoundTag data, ForgeIsland island, UUID newOwner) {
        String newOwnerName = PlayerNames.nameOf(Objects.requireNonNull(player.getServer()), newOwner);
        EnhancedDialog.open(player, "confirm_transfer", "Transfer island", EnhancedGuiHelper.getPlayerHead(newOwner),
                "Make " + newOwnerName + " the owner?",
                List.of(KeyValueRow.of("New owner", newOwnerName, EnhancedDialog.VALUE_WHITE),
                        KeyValueRow.of("You become", "Member", EnhancedDialog.VALUE_AQUA),
                        KeyValueRow.of("Undo", "only by the new owner", EnhancedDialog.VALUE_RED)),
                "Transfer", ButtonStyle.DANGER,
                p -> {
                    OwnershipTransferConfirmation.confirm(p, island, newOwner, newOwnerName);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:overview", data);
                },
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members", data));
    }
}
