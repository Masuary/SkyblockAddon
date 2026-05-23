package yorickbm.skyblockaddon.commands.interfaces;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public interface CommandStack {
    int execute(CommandSourceStack command, ServerPlayer executor);
}
