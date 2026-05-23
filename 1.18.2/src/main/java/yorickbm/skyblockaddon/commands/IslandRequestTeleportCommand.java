package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.FunctionRegistry;

import java.util.UUID;

public class IslandRequestTeleportCommand extends OverWorldCommandStack {
    public IslandRequestTeleportCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("request_tp")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Cmds.argument("player", EntityArgument.player())
                                .executes(context -> execute(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity(),
                                        EntityArgument.getPlayer(context, "player")
                                ))
                        )
                )
        );
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final ServerPlayer target) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        if(executor.getUUID().equals(target.getUUID())) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.request.self")));
            return Command.SINGLE_SUCCESS;
        }

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
            if (island == null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
                return;
            }

            //Register teleport function into registry
            final UUID functionKey = UUID.randomUUID();
            FunctionRegistry.registerFunction(functionKey, (e) -> {
                if(!super.isInOverworld(e)) return true;

                e.sendMessage(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("commands.request.other")
                        .formatted(executor.getDisplayName().getString()))
                    .withStyle(ChatFormatting.GREEN), e.getUUID());
                executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.request.accepted").formatted(e.getDisplayName().getString())).withStyle(ChatFormatting.GREEN), executor.getUUID());
                island.teleportTo(e);
                return true;
            }, 5); //Register function to teleport under hash 'functionKey' for 5 minutes.

            //Sending clickable message to accept request to requested
            command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.request.requested").formatted(target.getDisplayName().getString())).withStyle(ChatFormatting.GREEN), false);
            target.sendMessage(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("commands.request.request")
                        .formatted(executor.getDisplayName().getString()))
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, FunctionRegistry.getCommand(functionKey))))
                , target.getUUID());

        });

        return Command.SINGLE_SUCCESS;
    }
}
