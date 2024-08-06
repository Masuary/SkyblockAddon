package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.UUID;

public class ToggleVisibilityCommand {
    public ToggleVisibilityCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("island")
                .then(
                    Commands.literal("admin")
                        .requires(p -> p.hasPermission(3))
                        .then(
                            Commands.literal("toggle")
                                .then(Commands.argument("id", UuidArgument.uuid())
                                    .executes((command) -> execute(command.getSource(), UuidArgument.getUuid(command,"id")))
                                )
                        )
                )
        );
    }

    private int execute(CommandSourceStack command, UUID target) {
        if(!(command.getEntity() instanceof Player player)) { //Executed by non-player
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.player")));
            return Command.SINGLE_SUCCESS;
        }

        if(player.level.dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.overworld")));
            return Command.SINGLE_SUCCESS;
        }

        player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(g -> {
            IslandData island = g.getIslandById(target);
            if(island == null) {
                command.sendFailure(new TextComponent(String.format(SkyblockAddonLanguageConfig.getForKey("commands.not.found"), target)));
                return;
            }

            island.setTravelability(!island.getTravelability()); //Swap value
            command.sendSuccess(ServerHelper.formattedText(String.format(SkyblockAddonLanguageConfig.getForKey("commands.admin.toggle.success"), target, island.getTravelability() ? SkyblockAddonLanguageConfig.getForKey("guis.travels.public") : SkyblockAddonLanguageConfig.getForKey("guis.travels.private")), ChatFormatting.GREEN), true);
        });

        return Command.SINGLE_SUCCESS;
    }
}
