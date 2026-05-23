package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.NBTEncoder;

import java.nio.file.Path;
import java.util.UUID;

public class AdminLoadIslandCommand {

    public AdminLoadIslandCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Cmds.literal("island")
                .then(Cmds.literal("admin")
                        .requires(source -> source.getEntity() instanceof ServerPlayer
                                && source.hasPermission(Commands.LEVEL_MODERATORS))
                        .then(Cmds.literal("load")
                                .then(Cmds.argument("uuid", UuidArgument.uuid())
                                        .executes(context -> execute(
                                                context.getSource(),
                                                (ServerPlayer) context.getSource().getEntity(),
                                                UuidArgument.getUuid(context, "uuid")
                                        ))
                                )
                        )
                )
        );
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final UUID islandUUID) {

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {

            // Build path to islanddata folder (same as SkyblockAddonWorldCapability)
            final Path worldPath = command.getServer().getWorldPath(LevelResource.ROOT).normalize();
            final Path filePath = worldPath.resolve("islanddata");

            // Try to load the specific island file by UUID
            final ForgeIsland island = NBTEncoder.loadSingleFromFolder(filePath, ForgeIsland.class, islandUUID);

            if (island == null) {
                command.sendFailure(new TextComponent(
                        SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.not.found.uuid")
                                .formatted(islandUUID.toString())
                ));
                return;
            }

            // If already loaded, clear it from memory first before re-registering
            final Island existing = IslandManager.getInstance().getIslandByUUID(islandUUID);
            if (existing != null) {
                IslandManager.getInstance().clearIslandCache(existing);
            }

            // Register island into IslandManager (in-memory + cache)
            IslandManager.getInstance().registerIsland(island, island.getOwner());

            command.sendSuccess(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("commands.admin.island.loaded")
                            .formatted(islandUUID.toString())
            ), true);
        });

        return Command.SINGLE_SUCCESS;
    }
}