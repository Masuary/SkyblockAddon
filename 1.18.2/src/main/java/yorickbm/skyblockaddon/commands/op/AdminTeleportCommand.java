package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.UUID;

public class AdminTeleportCommand extends OverWorldCommandStack {
    public AdminTeleportCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
            .then(Cmds.literal("admin")
                .requires(source -> source.getEntity() instanceof ServerPlayer && source.hasPermission(Commands.LEVEL_MODERATORS))
                .then(Cmds.literal("tp")
                    .then(Cmds.argument("uuid", UuidArgument.uuid())
                        .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity(), UuidArgument.getUuid(context, "uuid")))
                    )
                )
            )
        );
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final UUID uuid) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(uuid);
            if (island == null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.not.found.uuid").formatted(uuid.toString())));
                return;
            }

            command.sendSuccess(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("commands.teleporting.other")
                            .formatted(UsernameCache.getBlocking(island.getOwner())))
                    .withStyle(ChatFormatting.GREEN), false);
            island.teleportTo(executor);
        });

        return Command.SINGLE_SUCCESS;
    }
}
