package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        EnhancedDialog.open(player, "confirm_transfer", "Transfer island?", new ItemStack(Items.GOLDEN_HELMET),
                List.of(new TextComponent("Make ").append(new TextComponent(newOwnerName).withStyle(ChatFormatting.AQUA))
                                .append(new TextComponent(" the owner?")),
                        new TextComponent("You stay on the island as a member").withStyle(ChatFormatting.GRAY),
                        new TextComponent("in the Members group.").withStyle(ChatFormatting.GRAY)),
                "Transfer", ButtonStyle.DANGER,
                p -> {
                    OwnershipTransferConfirmation.confirm(p, island, newOwner, newOwnerName);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:overview", data);
                },
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:transfer", data));
    }
}
