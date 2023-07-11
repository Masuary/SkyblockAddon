package yorickbm.skyblockaddon.commands.OP;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.SkyblockAddon;
import yorickbm.skyblockaddon.capabilities.Providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.capabilities.Providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.LanguageFile;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Collection;

public class WhereAmICommand {

    public WhereAmICommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("island")
                        .then(
                                Commands.literal("admin")
                                        .requires(p -> p.hasPermission(3))
                                        .then(
                                                Commands.literal("where")
                                                    .executes((command) -> execute(command.getSource()))
                                        )
                        )
        );
    }

    private int execute(CommandSourceStack command) {
        if(!(command.getEntity() instanceof Player player)) { //Executed by non-player
            command.sendFailure(new TextComponent(LanguageFile.getForKey("commands.island.nonplayer")));
            return Command.SINGLE_SUCCESS;
        }
        if(player.level.dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(LanguageFile.getForKey("commands.island.notoverworld")));
            return Command.SINGLE_SUCCESS;
        }

        command.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(g -> {
            String islandIdOn = g.getIslandIdByLocation(new Vec3i(player.getX(), 121, player.getZ()));
            if(islandIdOn == null || islandIdOn.equals("")) { //Not on an island so we do not affect permission
                command.sendFailure(new TextComponent(LanguageFile.getForKey("commands.island.admin.where.none")));
                return;
            }

            command.sendSuccess(
                ServerHelper.styledText(
                    LanguageFile.getForKey("commands.island.admin.where.success").formatted(""),
                    Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, ""))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(LanguageFile.getForKey("chat.hover.copy")))),
                    ChatFormatting.GREEN
                ),
                true
            );
        });

        return Command.SINGLE_SUCCESS;
    }

}
