package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
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

public class InviteIslandCommand {
    public InviteIslandCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("island").then(Commands.literal("invite").then(Commands.argument("targets", EntityArgument.players()).executes((command) -> { //.then(Commands.argument("name", MessageArgument.message()))
            return execute(command.getSource(), EntityArgument.getPlayers(command, "targets")); //, MessageArgument.getMessage(command, "name")
        }))));
    }

    private int execute(CommandSourceStack command, Collection<ServerPlayer> targets) { //, Component islandName
        if(!(command.getEntity() instanceof Player player)) { //Executed by non-player
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.player")));
            return Command.SINGLE_SUCCESS;
        }

        if(player.level.dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.overworld")));
            return Command.SINGLE_SUCCESS;
        }

        player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent( world -> player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(x -> {
            if(!world.getIslandById(x.getIslandId()).isIslandAdmin(player.getUUID())) {
                player.sendMessage(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("commands.not.permitted"), ChatFormatting.RED), player.getUUID());
                return;
            }

            if(targets.stream().findFirst().isEmpty()) {
                player.sendMessage(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("commands.invite.offline"), ChatFormatting.RED), player.getUUID());
                return;
            }

            ServerPlayer invitee = targets.stream().findFirst().get();
            invitee.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(s -> {
                if(s.hasOne()) {
                    player.sendMessage(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("commands.invite.has.one"), ChatFormatting.RED), player.getUUID());
                    return;
                }

                s.addInvite(x.getIslandId());
                invitee.sendMessage(
                        ServerHelper.styledText(
                                SkyblockAddonLanguageConfig.getForKey("commands.invite.message").formatted(player.getGameProfile().getName()),
                                Style.EMPTY
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/island join " + x.getIslandId()))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(SkyblockAddonLanguageConfig.getForKey("chat.hover.run.invite")))),
                                ChatFormatting.GREEN
                        ),
                        invitee.getUUID()
                );
                player.sendMessage(
                        ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("commands.invite.success").formatted(invitee.getGameProfile().getName()),
                                ChatFormatting.GREEN),
                        player.getUUID()
                );
            });
        }));

        return Command.SINGLE_SUCCESS;
    }
}
