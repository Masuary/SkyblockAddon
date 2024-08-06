package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.capabilities.providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Collection;
import java.util.UUID;

//execute as playername run island join islandId
public class SetPlayersIslandCommand {
    public SetPlayersIslandCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("island")
            .then(
                Commands.literal("admin")
                .requires(p -> p.hasPermission(3))
                .then(
                    Commands.literal("addmember")
                    .then(
                        Commands.argument("target", EntityArgument.players())
                        .then(Commands.argument("id", UuidArgument.uuid())
                            .executes((command) -> execute(command.getSource(), EntityArgument.getPlayers(command, "target"), UuidArgument.getUuid(command,"id")))
                        )
                    )
                )
            )
        );
    }

    private int execute(CommandSourceStack command, Collection<ServerPlayer> targets, UUID id) {
        if(!(command.getEntity() instanceof Player player)) { //Executed by non-player
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.player")));
            return Command.SINGLE_SUCCESS;
        }

        if(player.level.dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.overworld")));
            return Command.SINGLE_SUCCESS;
        }

        if(targets.stream().findFirst().isEmpty()) {
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.online")));
            return Command.SINGLE_SUCCESS;
        }
        Player target = targets.stream().findFirst().get();
        target.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(i -> player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(g -> {
            if(i.hasOne() && i.getIslandId().equals(id.toString())) {
                command.sendFailure(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("commands.admin.setId.override").formatted(target.getGameProfile().getName(), i.getIslandId())));
                return;
            }

            if(i.hasOne()) {
                player.sendMessage(ServerHelper.formattedText(
                        SkyblockAddonLanguageConfig.getForKey("commands.admin.setId.ispart").formatted(target.getGameProfile().getName(), i.getIslandId())
                        , ChatFormatting.GREEN, ChatFormatting.ITALIC
                ), player.getUUID());

                g.getIslandById(i.getIslandId()).removeIslandMember(player.getUUID());
            }

            g.getIslandById(id).addIslandMember(target.getUUID());
            i.setIsland(id);

            command.sendSuccess(
                ServerHelper.formattedText(
                        SkyblockAddonLanguageConfig.getForKey("commands.admin.setId.success").formatted(target.getGameProfile().getName(), id.toString()),
                    ChatFormatting.GREEN
                ),
                true
            );
            target.sendMessage(
                ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("commands.admin.setId.teleport"), ChatFormatting.GREEN)
                ,target.getUUID());

            g.getIslandById(id).teleport(target);
        }));

        return Command.SINGLE_SUCCESS;
    }
}
