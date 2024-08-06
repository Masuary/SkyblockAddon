package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.capabilities.providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.islands.IslandData;

import java.util.UUID;

public class CreateIslandCommand {

    public CreateIslandCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("island").then(Commands.literal("create").executes((command) -> { //.then(Commands.argument("name", MessageArgument.message()))
            return execute(command.getSource()); //, MessageArgument.getMessage(command, "name")
        })));
    }

    private int execute(CommandSourceStack command) { //, Component islandName

        if(!(command.getEntity() instanceof Player player)) { //Executed by non-player
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.player")));
            return Command.SINGLE_SUCCESS;
        }

        if(player.level.dimension() != Level.OVERWORLD) {
            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.not.overworld")));
            return Command.SINGLE_SUCCESS;
        }

        player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(island -> {

            if(island.hasOne()) {
                command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.create.has.one")));
                return;
            }

            long delay = island.CreateIslandDelay();
            if(delay > 0) {
                command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.create.delay").formatted(delay)));
                return;
            }

            player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(generator -> {
                    Thread asyncIslandGen = new Thread(() -> {
                        Vec3i vec = generator.genIsland(command.getLevel());
                        if(vec == null) {
                            command.sendFailure(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.create.fail")));
                            return;
                        }
                        player.sendMessage(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.create.generating")).withStyle(ChatFormatting.GREEN), player.getUUID());

                        IslandData islandData = new IslandData(player.getUUID(), vec);
                        UUID id = generator.registerIsland(islandData);
                        generator.saveIslandToFile(islandData, command.getServer()); //Create .nbt file

                        island.setIsland(id);
                        islandData.teleport(player);

                        command.sendSuccess(new TextComponent(SkyblockAddonLanguageConfig.getForKey("commands.create.success")).withStyle(ChatFormatting.GREEN), true);
                    });
                    asyncIslandGen.start();
            });
        });

        return Command.SINGLE_SUCCESS;
    }

}
