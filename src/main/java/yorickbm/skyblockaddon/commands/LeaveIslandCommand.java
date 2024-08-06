package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.PlayerIsland;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.capabilities.providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.ServerHelper;

public class LeaveIslandCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    public LeaveIslandCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("island").then(Commands.literal("leave").executes((command) -> { //.then(Commands.argument("name", MessageArgument.message()))
            return execute(command.getSource()); //, MessageArgument.getMessage(command, "name")
        })));
    }

    @SuppressWarnings("SameReturnValue")
    private int execute(CommandSourceStack command) { //, Component islandName

        if(!(command.getEntity() instanceof Player player)) { //Executed by non-player
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.player")));
            return Command.SINGLE_SUCCESS;
        }

        if(player.level.dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.overworld")));
            return Command.SINGLE_SUCCESS;
        }

        player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(island -> {
            if(!island.hasOne()) {
                command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.has.one")));
                return;
            }

            player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(generator -> {
                IslandData islandData = generator.getIslandById(island.getIslandId());

                LeaveIslandCommand.leaveIsland(islandData, island, player, command.getLevel());
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    public static void leaveIsland(IslandData islandData, PlayerIsland island, Player player, ServerLevel level) {
        islandData.removeIslandMember(player.getUUID());
        island.setIsland(null); //Make it empty so its NONE

        player.sendMessage(
            ServerHelper.formattedText(
                    SkyblockAddonLanguageConfig.getForKey("commands.leave.success"),
                ChatFormatting.GREEN),
            player.getUUID()
        );

        player.teleportTo(level.getSharedSpawnPos().getX(), level.getSharedSpawnPos().getY(), level.getSharedSpawnPos().getZ());
        ServerHelper.playSongToPlayer((ServerPlayer) player, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.4f, 1f);

        LOGGER.info(player.getDisplayName().getContents() + " ("+player.getStringUUID()+") has left island their island '"+island.getPreviousIsland()+"'.");

        island.addInvite(island.getPreviousIsland());
        player.sendMessage(
            ServerHelper.styledText(
                    SkyblockAddonLanguageConfig.getForKey("commands.leave.undo").formatted(player.getGameProfile().getName()),
                    Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/island join " + island.getPreviousIsland()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(SkyblockAddonLanguageConfig.getForKey("chat.hover.run.rejoin")))),
                ChatFormatting.GREEN
            ),
            player.getUUID()
        );
    }

}
