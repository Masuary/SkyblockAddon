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
import yorickbm.skyblockaddon.capabilities.PlayerIslandProvider;
import yorickbm.skyblockaddon.util.LanguageFile;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class InviteIslandCommand {
    public InviteIslandCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("island")
                    .requires(source -> {
                        if(source.getEntity() instanceof Player player) {
                            AtomicBoolean hasOne = new AtomicBoolean(false);
                            player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(i -> hasOne.set(i.hasOne()));
                            return hasOne.get();
                        }
                        return false;
                    })
                    .then(Commands.literal("invite").then(Commands.argument("targets", EntityArgument.players()).executes((command) -> { //.then(Commands.argument("name", MessageArgument.message()))
            return execute(command.getSource(), EntityArgument.getPlayers(command, "targets")); //, MessageArgument.getMessage(command, "name")
        }))));
    }

    private int execute(CommandSourceStack command, Collection<ServerPlayer> targets) { //, Component islandName
        Player player = (Player) command.getEntity();
        if(player.level.dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(LanguageFile.getForKey("commands.island.notoverworld")));
            return Command.SINGLE_SUCCESS;
        }

        player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(island -> {
            Optional<ServerPlayer> p = targets.stream().findFirst();
            p.ifPresent(serverPlayer -> serverPlayer.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(i -> {
                if (i.hasOne()) {
                    command.sendFailure(new TextComponent(LanguageFile.getForKey("commands.island.invite.hasone")));
                    return;
                }

                Style style = new TextComponent(LanguageFile.getForKey("commands.island.invite.invitation").formatted(player.getGameProfile().getName())).withStyle(ChatFormatting.GREEN).getStyle().withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/island accept")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent("Click to accept invite!")));

                serverPlayer.sendMessage(new TextComponent(LanguageFile.getForKey("commands.island.invite.invitation").formatted(player.getGameProfile().getName())).withStyle(style), serverPlayer.getUUID());
                player.sendMessage(new TextComponent(LanguageFile.getForKey("commands.island.invite.success").formatted(serverPlayer.getGameProfile().getName())).withStyle(ChatFormatting.GREEN), player.getUUID());

                i.request = player.getUUID();
                i.requestType = 1;
            }));

        });

        return Command.SINGLE_SUCCESS;
    }
}
