package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.util.UUID;

public class AdminGetIdCommand extends OverWorldCommandStack {
    public AdminGetIdCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Cmds.literal("island")
            .then(Cmds.literal("admin")
                .requires(source -> source.getEntity() instanceof ServerPlayer && source.hasPermission(Commands.LEVEL_MODERATORS))
                .then(Cmds.literal("getId")
                    .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity(), (ServerPlayer) null))
                    .then(Cmds.argument("player", EntityArgument.player())
                        .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity(), EntityArgument.getPlayer(context, "player")))
                    )
                    .then(Cmds.argument("uuid", UuidArgument.uuid())
                            .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity(), UuidArgument.getUuid(context, "uuid")))
                    )
                )
            )
        );
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final ServerPlayer target) {

        if(target == null) { //Require over-world if admin is not running a player check
            if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

            command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
                final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(executor.getOnPos()));
                if (island == null) {
                    command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.no.island")));
                    return;
                }

                command.sendSuccess(
                        new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.found.id").formatted(island.getId().toString()))
                                .withStyle(ChatFormatting.GREEN)
                                .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, island.getId().toString())))
                        , false);
            });

            return Command.SINGLE_SUCCESS;
        }

        execute(command, executor, target.getUUID());

        return Command.SINGLE_SUCCESS;
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final UUID target) {

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(target);
            if (island == null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.not.found.player").formatted(UsernameCache.getBlocking(target))));
                return;
            }

            command.sendSuccess(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.found.id").formatted(island.getId().toString()))
                            .withStyle(ChatFormatting.GREEN)
                            .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, island.getId().toString())))
                    , false);
        });

        return Command.SINGLE_SUCCESS;
    }
}
