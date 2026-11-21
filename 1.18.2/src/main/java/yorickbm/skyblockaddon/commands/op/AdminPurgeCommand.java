package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.chunk.ChunkTaskScheduler;
import yorickbm.skyblockaddon.chunk.IslandChunkManager;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.FunctionRegistry;
import yorickbm.skyblockaddon.util.ProgressBar;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminPurgeCommand {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CONFIRM_LITERAL = "confirm";
    private static final int CONFIRMATION_LINK_MINUTES = 5;

    public AdminPurgeCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(
                Cmds.literal(rootLiteral)
                        .then(Cmds.adminLiteral()
                                .then(Cmds.literal("purge")
                                        .requires(Cmds.permissionLevel(Commands.LEVEL_GAMEMASTERS))
                                        .then(Cmds.argument("target", UuidArgument.uuid())
                                                .executes(ctx -> purgeIsland(ctx, false, false))
                                                .then(Cmds.literal(CONFIRM_LITERAL)
                                                        .executes(ctx -> purgeIsland(ctx, false, true)))
                                                .then(Cmds.argument("clearCache", IntegerArgumentType.integer(0, 1))
                                                        .executes(ctx -> purgeIsland(ctx, clearCacheRequested(ctx), false))
                                                        .then(Cmds.literal(CONFIRM_LITERAL)
                                                                .executes(ctx -> purgeIsland(ctx, clearCacheRequested(ctx), true)))
                                                )
                                        )
                                        .then(Cmds.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> purgeFirstPurgeableIslands(ctx, false, false))
                                                .then(Cmds.literal(CONFIRM_LITERAL)
                                                        .executes(ctx -> purgeFirstPurgeableIslands(ctx, false, true)))
                                                .then(Cmds.argument("clearCache", IntegerArgumentType.integer(0, 1))
                                                        .executes(ctx -> purgeFirstPurgeableIslands(ctx, clearCacheRequested(ctx), false))
                                                        .then(Cmds.literal(CONFIRM_LITERAL)
                                                                .executes(ctx -> purgeFirstPurgeableIslands(ctx, clearCacheRequested(ctx), true)))
                                                )
                                        )
                                )
                        )
        );
    }

    private static boolean clearCacheRequested(final CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "clearCache") > 0;
    }

    private int purgeIsland(final CommandContext<CommandSourceStack> ctx, final boolean clearCache, final boolean confirmed) {
        final CommandSourceStack source = ctx.getSource();
        final UUID islandId = UuidArgument.getUuid(ctx, "target");
        final SkyblockAddonWorldCapability capability = overworldCapability(source);

        if (!capability.getPurgableIslands().contains(islandId)) {
            source.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.notallowed")));
            return 0;
        }

        requestOrStartPurge(ctx, capability, List.of(islandId), clearCache, confirmed);
        return Command.SINGLE_SUCCESS;
    }

    private int purgeFirstPurgeableIslands(final CommandContext<CommandSourceStack> ctx, final boolean clearCache, final boolean confirmed) {
        final CommandSourceStack source = ctx.getSource();
        final int count = IntegerArgumentType.getInteger(ctx, "count");
        final SkyblockAddonWorldCapability capability = overworldCapability(source);

        final List<UUID> purgeableIslandIds = capability.getPurgableIslands();
        if (purgeableIslandIds.isEmpty()) {
            source.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.zero")));
            return 0;
        }

        requestOrStartPurge(ctx, capability, purgeableIslandIds.stream().limit(count).toList(), clearCache, confirmed);
        return Command.SINGLE_SUCCESS;
    }

    private static SkyblockAddonWorldCapability overworldCapability(final CommandSourceStack source) {
        return source.getServer().overworld()
                .getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("SkyblockAddon world capability is not attached to the overworld"));
    }

    private void requestOrStartPurge(final CommandContext<CommandSourceStack> ctx, final SkyblockAddonWorldCapability capability,
                                     final List<UUID> islandIds, final boolean clearCache, final boolean confirmed) {
        final CommandSourceStack source = ctx.getSource();
        if (confirmed) {
            startPurge(source, capability, islandIds, clearCache);
            return;
        }

        if (source.getEntity() instanceof ServerPlayer) {
            final UUID functionKey = UUID.randomUUID();
            FunctionRegistry.registerFunction(functionKey, clickingPlayer -> {
                startPurge(source, capability, islandIds, clearCache);
                return true;
            }, CONFIRMATION_LINK_MINUTES);

            source.sendSuccess(new TextComponent(
                            SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.ask")
                                    .formatted(String.valueOf(islandIds.size())))
                            .withStyle(ChatFormatting.GREEN)
                            .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    FunctionRegistry.getCommand(functionKey)))),
                    false);
            return;
        }

        source.sendSuccess(new TextComponent(
                        SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.ask.console")
                                .formatted(String.valueOf(islandIds.size()), "/" + ctx.getInput() + " " + CONFIRM_LITERAL))
                        .withStyle(ChatFormatting.GREEN),
                false);
    }

    private static void startPurge(final CommandSourceStack source, final SkyblockAddonWorldCapability capability,
                                   final List<UUID> islandIds, final boolean clearCache) {
        ChunkTaskScheduler.init();
        if (clearCache) ChunkTaskScheduler.clear();

        final List<ForgeIsland> islands = islandIds.stream()
                .map(islandId -> (ForgeIsland) IslandManager.getInstance().getIslandByUUID(islandId))
                .filter(Objects::nonNull)
                .filter(Island::hasNoOwnerAndNoMembers)
                .toList();

        final ProgressBar bar = new ProgressBar(new TextComponent("Purging "));
        if (source.getEntity() instanceof ServerPlayer player) bar.start(player);
        LOGGER.info("Purge of {} abandoned island(s) started by {}", islands.size(), source.getTextName());

        final AtomicInteger purgedIslandCount = new AtomicInteger();
        IslandChunkManager.createDummies(islands, bar, AdminPurgeCommand::isStillRegisteredAndAbandoned,
                island -> finishIsland(source, capability, bar, island, purgedIslandCount),
                () -> {
                    LOGGER.info("Purge finished: {} of {} island(s) purged", purgedIslandCount.get(), islands.size());
                    bar.kill();
                    source.sendSuccess(new TextComponent(
                            SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.done")
                                    .formatted(String.valueOf(purgedIslandCount.get())))
                            .withStyle(ChatFormatting.GREEN), false);
                },
                source.getServer().overworld());
    }

    private static void finishIsland(final CommandSourceStack source, final SkyblockAddonWorldCapability capability,
                                     final ProgressBar bar, final ForgeIsland island, final AtomicInteger purgedIslandCount) {
        if (!isStillRegisteredAndAbandoned(island)) {
            LOGGER.warn("Purge skipped island {}: it has an owner or members again or was unloaded", island.getId());
            source.sendSuccess(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("commands.admin.purge.skipped")
                            .formatted(island.getId().toString()))
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        LOGGER.debug("Finished the island {}", island.getId());
        bar.sendToast(new TextComponent("Completed purging island " + island.getId()));

        IslandManager.getInstance().islandSpaceReusable(island.getCenter());
        capability.removeIslandNBT(island);
        IslandManager.getInstance().clearIslandCache(island);
        purgedIslandCount.incrementAndGet();
    }

    private static boolean isStillRegisteredAndAbandoned(final ForgeIsland island) {
        return IslandManager.getInstance().getIslandByUUID(island.getId()) == island && island.hasNoOwnerAndNoMembers();
    }
}
