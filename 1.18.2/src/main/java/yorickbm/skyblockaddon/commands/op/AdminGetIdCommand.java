package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.util.UUID;

public class AdminGetIdCommand extends OverWorldCommandStack {
    public AdminGetIdCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
            .then(Cmds.adminLiteral()
                .then(Cmds.literal("getId")
                    .requires(Cmds.permissionLevel(Commands.LEVEL_MODERATORS))
                    .executes(context -> executeForStandingPosition(context.getSource(), context.getSource().getPlayerOrException()))
                    .then(Cmds.argument("player", EntityArgument.player())
                        .executes(context -> executeForPlayer(context.getSource(), EntityArgument.getPlayer(context, "player").getUUID()))
                    )
                    .then(Cmds.argument("uuid", UuidArgument.uuid())
                            .executes(context -> executeForPlayer(context.getSource(), UuidArgument.getUuid(context, "uuid")))
                    )
                )
            )
        );
    }

    private int executeForStandingPosition(final CommandSourceStack command, final ServerPlayer executor) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByPos(ForgeConverter.forgeToInternalVec3i(executor.getOnPos()));
        if (island == null) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.no.island")));
            return Command.SINGLE_SUCCESS;
        }

        sendIslandId(command, island);
        return Command.SINGLE_SUCCESS;
    }

    private int executeForPlayer(final CommandSourceStack command, final UUID target) {
        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(target);
        if (island == null) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.not.found.player").formatted(UsernameCache.getBlocking(target))));
            return Command.SINGLE_SUCCESS;
        }

        sendIslandId(command, island);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendIslandId(final CommandSourceStack command, final ForgeIsland island) {
        command.sendSuccess(
                new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.found.id").formatted(island.getId().toString()))
                        .withStyle(ChatFormatting.GREEN)
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, island.getId().toString())))
                , false);
    }
}
