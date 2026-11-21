package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;

public class IslandCommand extends OverWorldCommandStack {
    public IslandCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(
                Cmds.literal(rootLiteral)
                        .executes(context -> execute(
                                context.getSource(),
                                context.getSource().getPlayerOrException()))
        );
    }

    @Override
    public int execute(final CommandSourceStack command, final ServerPlayer executor) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final Island island = IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
        if (island == null) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
            return Command.SINGLE_SUCCESS;
        }

        final CompoundTag tag = new CompoundTag();
        tag.putUUID(GuiDataKeys.ISLAND_ID, island.getId());
        GUILibraryRegistry.openGUIForPlayer(executor, SkyblockAddonCore.MOD_ID + ":overview", tag);

        return Command.SINGLE_SUCCESS;
    }
}
