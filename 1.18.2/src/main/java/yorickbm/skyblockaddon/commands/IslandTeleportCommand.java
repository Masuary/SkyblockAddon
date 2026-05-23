package yorickbm.skyblockaddon.commands;

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
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.FunctionRegistry;

import java.util.UUID;

public class IslandTeleportCommand extends OverWorldCommandStack {
    public IslandTeleportCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("tp")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> executePersonal(
                                context.getSource(),
                                (ServerPlayer) context.getSource().getEntity()))
                        .then(Cmds.argument("player", EntityArgument.player())
                                .executes(context -> executeRequest(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity(),
                                        EntityArgument.getPlayer(context, "player")))
                        )
                        .then(Cmds.argument("uuid", UuidArgument.uuid())
                                .requires(source ->
                                        source.getEntity() instanceof ServerPlayer &&
                                                source.hasPermission(Commands.LEVEL_MODERATORS))
                                .executes(context -> executeAdmin(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity(),
                                        UuidArgument.getUuid(context, "uuid")))
                        )
                )
        );
    }

    private int executeAdmin(final CommandSourceStack command, final ServerPlayer executor, final UUID uuid) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(uuid);
            if (island == null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.not.found.player").formatted(UsernameCache.getBlocking(uuid))));
                return;
            }

            command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.other").formatted(UsernameCache.getBlocking(uuid))).withStyle(ChatFormatting.GREEN), false);
            island.teleportTo(executor);
        });

        return Command.SINGLE_SUCCESS;
    }

    private int executePersonal(final CommandSourceStack command, final ServerPlayer executor) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
            if (island == null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
                return;
            }

            command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.own")).withStyle(ChatFormatting.GREEN), false);
            island.teleportTo(executor);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeRequest(final CommandSourceStack command, final ServerPlayer executor, final ServerPlayer requested) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        //Requesting to teleport to his own island
        if(executor.getUUID().equals(requested.getUUID())) {
            return executePersonal(command, executor); //Run personal execution with command details
        }

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(requested.getUUID());
            if (island == null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
                return;
            }

            //Determine if it is a public island
            //Determine if user is OP
            if(island.isVisible() || executor.hasPermissions(Commands.LEVEL_MODERATORS)) {
                command.sendSuccess(new TextComponent(
                        SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.other")
                                .formatted(requested.getDisplayName().getString()))
                        .withStyle(ChatFormatting.GREEN), false);
                requested.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.accepted").formatted(executor.getDisplayName().getString())), requested.getUUID());
                island.teleportTo(executor);
                return;
            }

            //Register teleport function into registry
            final UUID functionKey = UUID.randomUUID();
            FunctionRegistry.registerFunction(functionKey, (e) -> {
                if(!super.isInOverworld(e)) return true;

                executor.sendMessage(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.other")
                        .formatted(e.getDisplayName().getString()))
                    .withStyle(ChatFormatting.GREEN), e.getUUID());
                e.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.accepted").formatted(executor.getDisplayName().getString())).withStyle(ChatFormatting.GREEN), requested.getUUID());
                island.teleportTo(executor);
                return true;
            }, 5); //Register function to teleport under hash 'functionKey' for 5 minutes.

            //Sending clickable message to accept request to requested
            command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.requested").formatted(UsernameCache.getBlocking(island.getOwner()))).withStyle(ChatFormatting.GREEN), false);
            requested.sendMessage(new TextComponent(
                SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.request")
                        .formatted(executor.getDisplayName().getString()))
                .withStyle(ChatFormatting.GREEN)
                .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, FunctionRegistry.getCommand(functionKey))))
                , requested.getUUID());
        });

        return Command.SINGLE_SUCCESS;
    }
}
