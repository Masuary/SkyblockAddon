package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;

public class IslandExpelCommand extends OverWorldCommandStack {
    public IslandExpelCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is");
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("expel")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Cmds.argument("player", EntityArgument.player())
                                .executes(context -> execute(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity(),
                                        EntityArgument.getPlayer(context, "player")))
                        )
                )
        );
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final ServerPlayer target) {
        if (super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
        if (island == null) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
            return Command.SINGLE_SUCCESS;
        }

        final String targetName = target.getDisplayName().getString();
        final IslandActionResult result = IslandActionService.expelVisitor(executor, island, target);
        if (!result.succeeded()) {
            command.sendFailure(IslandActionService.failureMessage(result, targetName));
            return Command.SINGLE_SUCCESS;
        }
        command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.expel.success").formatted(targetName))
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }
}
