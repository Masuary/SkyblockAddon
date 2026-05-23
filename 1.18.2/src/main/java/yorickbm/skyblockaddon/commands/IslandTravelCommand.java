package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

public class IslandTravelCommand extends OverWorldCommandStack {
    public IslandTravelCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("travel")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> execute(
                                context.getSource(),
                                (ServerPlayer) context.getSource().getEntity()))
                )
        );
    }

    @Override
    public int execute(final CommandSourceStack command, final ServerPlayer executor) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        GUILibraryRegistry.openGUIForPlayer(executor, SkyblockAddonCore.MOD_ID + ":travel");
        return Command.SINGLE_SUCCESS;
    }
}
