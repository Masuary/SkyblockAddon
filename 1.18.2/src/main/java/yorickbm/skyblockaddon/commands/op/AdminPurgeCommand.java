package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.chunk.ChunkTaskScheduler;
import yorickbm.skyblockaddon.chunk.IslandChunkManager;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.FunctionRegistry;
import yorickbm.skyblockaddon.util.ProgressBar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminPurgeCommand extends OverWorldCommandStack {
    private static final Logger LOGGER = LogManager.getLogger();

    public AdminPurgeCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Cmds.literal("island")
                        .then(Cmds.literal("admin")
                                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Cmds.literal("purge")
                                        .then(Cmds.argument("target", UuidArgument.uuid())
                                                .executes(ctx -> executeUUID(
                                                        ctx.getSource(),
                                                        UuidArgument.getUuid(ctx, "target"),
                                                        0))
                                                .then(Cmds.argument("clearCache", IntegerArgumentType.integer(0, 1))
                                                        .executes(ctx -> executeUUID(
                                                                ctx.getSource(),
                                                                UuidArgument.getUuid(ctx, "target"),
                                                                IntegerArgumentType.getInteger(ctx, "clearCache")
                                                        ))
                                                )
                                        )
                                        .then(Cmds.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> executeAmount(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        0))
                                                .then(Cmds.argument("clearCache", IntegerArgumentType.integer(0, 1))
                                                        .executes(ctx -> executeAmount(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                                IntegerArgumentType.getInteger(ctx, "clearCache")
                                                        ))
                                                )
                                        )
                                )
                        )
        );
    }

    private ServerPlayer playerOrNull(final CommandSourceStack command) {
        return command.getEntity() instanceof ServerPlayer sp ? sp : null;
    }

    private int executeUUID(final CommandSourceStack command, final UUID uuid, final int clearCache) {
        final ServerPlayer executor = playerOrNull(command);
        if (super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        Objects.requireNonNull(command.getServer().getLevel(Level.OVERWORLD))
                .getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final List<UUID> purgable = new ArrayList<>(cap.getPurgableIslands());

            if (!purgable.contains(uuid)) {
                command.sendFailure(new TextComponent(
                        SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.notallowed"))
                        .withStyle(ChatFormatting.RED));
                return;
            }

            promptOrExecute(command, executor, cap, List.of(uuid), clearCache);
        });

        return Command.SINGLE_SUCCESS;
    }

    private int executeAmount(final CommandSourceStack command, final int count, final int clearCache) {
        final ServerPlayer executor = playerOrNull(command);
        if (super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        Objects.requireNonNull(command.getServer().getLevel(Level.OVERWORLD))
                .getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final List<UUID> purgable = new ArrayList<>(cap.getPurgableIslands());

            if (purgable.isEmpty()) {
                command.sendFailure(new TextComponent(
                        SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.zero"))
                        .withStyle(ChatFormatting.RED));
                return;
            }

            final List<UUID> selected = purgable.stream().limit(count).collect(Collectors.toList());
            promptOrExecute(command, executor, cap, selected, clearCache);
        });

        return Command.SINGLE_SUCCESS;
    }

    private void promptOrExecute(final CommandSourceStack command, final ServerPlayer executor,
                                  final SkyblockAddonWorldCapability cap, final List<UUID> islandUUIDs,
                                  final int clearCache) {
        if (executor != null) {
            // In-game: show click-to-confirm prompt
            final UUID functionKey = UUID.randomUUID();
            FunctionRegistry.registerFunction(functionKey, (e) -> {
                startPurgeWithIslands(command, executor, cap, islandUUIDs, clearCache);
                return true;
            }, 5);

            command.sendSuccess(new TextComponent(
                            SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.ask")
                                    .formatted(String.valueOf(islandUUIDs.size())))
                            .withStyle(ChatFormatting.GREEN)
                            .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FunctionRegistry.getCommand(functionKey)))),
                    false);
        } else {
            // Console: no confirmation needed, execute directly
            command.sendSuccess(new TextComponent(
                    "Purging " + islandUUIDs.size() + " island(s) from console...")
                    .withStyle(ChatFormatting.YELLOW), true);
            startPurgeWithIslands(command, null, cap, islandUUIDs, clearCache);
        }
    }

    private void startPurgeWithIslands(final CommandSourceStack command, final ServerPlayer executor,
                                       final SkyblockAddonWorldCapability cap, final List<UUID> islandUUIDs,
                                       final int clearCache) {
        ChunkTaskScheduler.init();
        if (clearCache > 0) ChunkTaskScheduler.clear();

        final List<ForgeIsland> islands = islandUUIDs.stream()
                .map(s -> (ForgeIsland) IslandManager.getInstance().getIslandByUUID(s))
                .filter(Objects::nonNull)
                .filter(Island::isAbandoned)
                .collect(Collectors.toList());

        final ProgressBar bar = executor != null ? new ProgressBar(new TextComponent("Purging ")) : null;
        if (bar != null) bar.start(executor);

        final IslandChunkManager manager = new IslandChunkManager();
        manager.createDummies(islands, bar, island -> {
                    LOGGER.debug("Finished the island {}", island.getId());
                    if (bar != null) bar.sendToast(new TextComponent("Completed purging island " + island.getId()));

                    IslandManager.getInstance().islandSpaceReusable(island.getCenter());
                    cap.removeIslandNBT(island);
                    IslandManager.getInstance().clearIslandCache(island);
                },
                () -> {
                    LOGGER.debug("Finished purging");

                    if (bar != null) {
                        command.getServer().tell(new TickTask(command.getServer().getTickCount() + 16, bar::kill));
                    }

                    final String doneMsg = SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.done")
                            .formatted(String.valueOf(islands.size()));
                    if (executor != null) {
                        executor.sendMessage(new TextComponent(doneMsg).withStyle(ChatFormatting.GREEN), executor.getUUID());
                    } else {
                        command.sendSuccess(new TextComponent(doneMsg).withStyle(ChatFormatting.GREEN), true);
                    }
                },
                Objects.requireNonNull(command.getServer().getLevel(Level.OVERWORLD)));
    }
}
