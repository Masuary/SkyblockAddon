package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.util.UUID;

public class AdminMenuCommand extends OverWorldCommandStack {
    public AdminMenuCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("admin")
                        .requires(source -> source.getEntity() instanceof ServerPlayer && source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Cmds.literal("menu")
                                .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity(), null))
                                .then(Cmds.argument("id", UuidArgument.uuid())
                                        .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity(), UuidArgument.getUuid(context, "id")))
                                )
                        )
                )
        );
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final UUID islandId) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            ForgeIsland island = null;

            if (islandId == null) island = (ForgeIsland) IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(executor.getOnPos()));
            else island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(islandId);

            if (island == null) {
                if (islandId == null)
                    command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.no.island")));
                else
                    command.sendFailure(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.not.found.uuid"), islandId.toString())));
                return;
            }

            final CompoundTag tag = new CompoundTag();
            tag.putUUID("island_id", island.getId());
            GUILibraryRegistry.openGUIForPlayer(executor, SkyblockAddonCore.MOD_ID + ":overview", tag);
        });
        return Command.SINGLE_SUCCESS;
    }
}
