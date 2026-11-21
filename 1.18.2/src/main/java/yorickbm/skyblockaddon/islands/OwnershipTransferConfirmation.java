package yorickbm.skyblockaddon.islands;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandActions;
import yorickbm.skyblockaddon.util.FunctionRegistry;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.Objects;
import java.util.UUID;

/**
 * The chat confirmation of an ownership transfer, used by /island transfer and the vanilla-client GUI.
 */
public final class OwnershipTransferConfirmation {
    private static final int CONFIRMATION_MINUTES = 5;

    private OwnershipTransferConfirmation() {
    }

    public static void offer(final ServerPlayer owner, final ForgeIsland island, final UUID newOwner) {
        final String newOwnerName = PlayerNames.nameOf(Objects.requireNonNull(owner.getServer()), newOwner);
        final IslandActionResult check = IslandActions.checkTransferOwnership(island, IslandActionService.actorOf(owner), newOwner);
        if (!check.succeeded()) {
            IslandActionService.sendFailure(owner, check, newOwnerName);
            return;
        }

        final UUID functionKey = UUID.randomUUID();
        FunctionRegistry.registerFunction(functionKey, clicker -> {
            confirm(clicker, island, newOwner, newOwnerName);
            return true;
        }, CONFIRMATION_MINUTES);

        owner.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.transfer.confirm").formatted(newOwnerName))
                        .withStyle(ChatFormatting.GOLD)
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, FunctionRegistry.getCommand(functionKey)))),
                owner.getUUID());
    }

    public static void confirm(final ServerPlayer owner, final ForgeIsland island, final UUID newOwner, final String newOwnerName) {
        final IslandActionResult result = IslandActionService.transferOwnership(owner, island, newOwner);
        if (!result.succeeded()) {
            IslandActionService.sendFailure(owner, result, newOwnerName);
            return;
        }
        owner.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.transfer.success").formatted(newOwnerName))
                .withStyle(ChatFormatting.GREEN), owner.getUUID());
    }
}
