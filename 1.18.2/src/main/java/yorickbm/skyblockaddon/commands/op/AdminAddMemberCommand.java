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
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.Objects;
import java.util.UUID;

public class AdminAddMemberCommand {
    public AdminAddMemberCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
            .then(Cmds.literal("admin")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Cmds.literal("addMember")
                    .then(Cmds.argument("islandId", UuidArgument.uuid())
                        .then(Cmds.argument("player", EntityArgument.player())
                                .executes(context -> execute(context.getSource(),
                                        UuidArgument.getUuid(context, "islandId"),
                                        EntityArgument.getPlayer(context, "player")))
                        )
                        .then(Cmds.argument("uuid", UuidArgument.uuid())
                                .executes(context -> execute(context.getSource(),
                                        UuidArgument.getUuid(context, "islandId"),
                                        UuidArgument.getUuid(context, "uuid")))
                        )
                    )
                )
            )
        );
    }

    public int execute(final CommandSourceStack command, final UUID islandId, final ServerPlayer target) {
        execute(command, islandId, target.getUUID()); //Convert target into UUID
        return Command.SINGLE_SUCCESS;
    }

    public int execute(final CommandSourceStack command, final UUID islandId, final UUID target) {

        Objects.requireNonNull(command.getServer().getLevel(Level.OVERWORLD)).getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(islandId);
            if (island == null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.not.found.uuid").formatted(islandId)));
                return;
            }

            //Determine if target does not already have an island
            if(IslandManager.getInstance().getIslandByEntityUUID(target) != null) {
                if(island.isPartOf(target)) {
                    command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.member.part").formatted(UsernameCache.getBlocking(target))));
                    return;
                } else {
                    command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.has.island").formatted(UsernameCache.getBlocking(target))));
                    return;
                }
            }

            //Add target to islands basic group
            if(!island.addMember(target, SkyblockAddonCore.MOD_UUID)) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.member.failure").formatted(UsernameCache.getBlocking(target), UsernameCache.getBlocking(island.getOwner()))));
                return;
            }

            //Inform admin
            command.sendSuccess(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.member.added").formatted(UsernameCache.getBlocking(target), UsernameCache.getBlocking(island.getOwner())))
                            .withStyle(ChatFormatting.GREEN)
                    , false);
        });

        return Command.SINGLE_SUCCESS;
    }
}
