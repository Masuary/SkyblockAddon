package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;

public class IslandHubCommand extends OverWorldCommandStack {
    public IslandHubCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Cmds.literal("hub")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity()))
        );
    }

    @Override
    public int execute(final CommandSourceStack command, final ServerPlayer executor) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        executor.teleportTo(command.getLevel().getSharedSpawnPos().getX(), command.getLevel().getSharedSpawnPos().getY(), command.getLevel().getSharedSpawnPos().getZ());

        return Command.SINGLE_SUCCESS;
    }
}
