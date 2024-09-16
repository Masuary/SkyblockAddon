package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.configs.SkyBlockAddonLanguage;

public class HubCommand extends OverWorldCommandStack {
    public HubCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hub")
                .requires(source -> source.getEntity() instanceof ServerPlayer) // Ensure the command is used by a player
                .executes(context -> execute(context.getSource(), (ServerPlayer) context.getSource().getEntity())));
    }

    @Override
    public int execute(CommandSourceStack command, ServerPlayer player) {
        // Check if the player is in the Overworld
        if (player.getLevel().dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.not.in.overworld"))
                    .withStyle(ChatFormatting.RED));
            return Command.SINGLE_SUCCESS;
        }

        // Get the world spawn position
        BlockPos worldSpawn = player.getLevel().getSharedSpawnPos();

        // Teleport the player to the world spawn position
        player.teleportTo(worldSpawn.getX() + 0.5, worldSpawn.getY(), worldSpawn.getZ() + 0.5); // Adding 0.5 to center the player on the block

        return Command.SINGLE_SUCCESS;
    }
}
