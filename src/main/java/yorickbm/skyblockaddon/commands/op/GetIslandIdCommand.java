package yorickbm.skyblockaddon.commands.op;

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
import yorickbm.skyblockaddon.capabilities.providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Collection;

public class GetIslandIdCommand {
    public GetIslandIdCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("island")
            .then(
                Commands.literal("admin")
                .requires(p -> p.hasPermission(3))
                .then(
                    Commands.literal("getId")
                    .then(
                        Commands.argument("target", EntityArgument.players())
                        .executes((command) -> execute(command.getSource(), EntityArgument.getPlayers(command, "target")))
                    )
                )
            )
        );
    }

    private int execute(CommandSourceStack command, Collection<ServerPlayer> targets) {
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
        targets.stream().findFirst().get().getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(i -> {
           if(!i.hasOne()) {
               command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.has.island")));
               return;
           }

            player.sendMessage(
                ServerHelper.styledText(
                        SkyblockAddonLanguageConfig.getForKey("commands.admin.getId.success").formatted(targets.stream().findFirst().get().getGameProfile().getName()),
                    Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, i.getIslandId().toString()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(SkyblockAddonLanguageConfig.getForKey("chat.hover.copy")))),
                    ChatFormatting.GREEN
                ),
                    player.getUUID()
            );

           command.sendSuccess(ServerHelper.formattedText("-> " + i.getIslandId(), ChatFormatting.GREEN), false);

        });

        return Command.SINGLE_SUCCESS;
    }
}
