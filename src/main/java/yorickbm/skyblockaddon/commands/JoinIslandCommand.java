package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.capabilities.providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.UUID;

public class JoinIslandCommand {
    public JoinIslandCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("island")
            .then(Commands.literal("join")
                .then(
                    Commands.argument("islandId", UuidArgument.uuid())
                    .executes((command) -> execute(command.getSource(), UuidArgument.getUuid(command, "islandId")))
                )
            )
        );
    }

    private int execute(CommandSourceStack command, UUID islandId) { //, Component islandName
        Player player = (Player) command.getEntity();
        if(player.getLevel().dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.overworld")));
            return Command.SINGLE_SUCCESS;
        }

        player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(playerIsland -> {
            if(playerIsland.hasOne()) {
                command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.accept.has.one")));
                return;
            }
            if(!playerIsland.inviteValid(islandId)) {
                command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.accept.invalid")));
                return;
            }

            player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(g -> {
                //Update island data
                IslandData island = g.getIslandById(islandId);
                island.addIslandMember(player.getUUID());
                playerIsland.setIsland(islandId);

                //Inform player
                ServerHelper.playSongToPlayer((ServerPlayer) player, SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 1f);
                command.sendSuccess(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.accept.success").formatted(island.getOwner(player.getServer()).getName())).withStyle(ChatFormatting.GREEN), false);

                //Teleport to the island
                island.teleport(player);
            });
        });

        return Command.SINGLE_SUCCESS;
    }
}
