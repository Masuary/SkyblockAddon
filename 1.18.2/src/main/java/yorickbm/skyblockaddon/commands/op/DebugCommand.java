package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;

public class DebugCommand {
    public DebugCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Cmds.literal("island")
                .then(Cmds.literal("admin")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Cmds.literal("debug")
                                .executes(context -> execute(context.getSource()))
                        )
                )
        );
    }

    public int execute(final CommandSourceStack command) {
        final String def = SkyblockAddonConfig.getForKey("permissions.debug");
        SkyblockAddonConfig.setValueForKey("permissions.debug", def.equalsIgnoreCase("TRUE") ? "FALSE" : "TRUE");

        command.sendSuccess(new TextComponent("Debug messages have been " + (def.equalsIgnoreCase("TRUE") ? "disabled" : "enabled") + " for permissions in console.").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }
}
