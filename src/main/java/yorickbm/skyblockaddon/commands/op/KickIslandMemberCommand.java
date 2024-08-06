package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.capabilities.providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Collection;
import java.util.UUID;

public class KickIslandMemberCommand {
    public KickIslandMemberCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("island")
                        .then(
                                Commands.literal("admin")
                                        .requires(p -> p.hasPermission(3))
                                        .then(Commands.literal("kick").then(Commands.argument("target", EntityArgument.players())
                                                .executes( (command) -> execute(command.getSource(), EntityArgument.getPlayers(command, "target"), null))
                                                .then(Commands.argument("id", UuidArgument.uuid()) //Optional argument?!
                                                        .executes( (command) -> execute(command.getSource(), EntityArgument.getPlayers(command, "target"), UuidArgument.getUuid(command,"id")))
                                                )
                                        ))
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
            IslandData island = (id == null) ? g.getIslandById(i.getIslandId()) : g.getIslandById(id);
            if(island == null) {
                command.sendFailure(new TextComponent(String.format(SkyblockAddonLanguageConfig.getForKey("commands.not.found"), (id == null) ? i.getIslandId() : id.toString())));
                return;
            }
            if(!island.isMember(target.getUUID())) {
                command.sendFailure(new TextComponent(String.format(SkyblockAddonLanguageConfig.getForKey("commands.not.part"), target.getGameProfile().getName(), (id == null) ? i.getIslandId() : id.toString())));
                return;
            }

            island.removeIslandMember(target.getUUID());
            i.setIsland(null);

            command.sendSuccess(ServerHelper.formattedText(String.format(SkyblockAddonLanguageConfig.getForKey("commands.admin.island.kick.success"), target.getGameProfile().getName(), (id == null) ? i.getIslandId() : id.toString()), ChatFormatting.GREEN), true);

            target.teleportTo(((ServerLevel) player.getLevel()).getSharedSpawnPos().getX(), ((ServerLevel) player.getLevel()).getSharedSpawnPos().getY(),((ServerLevel) player.getLevel()).getSharedSpawnPos().getZ());
            target.sendMessage(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("island.member.kick"), ChatFormatting.GREEN), target.getUUID());
        }));

        return Command.SINGLE_SUCCESS;
    }
}
