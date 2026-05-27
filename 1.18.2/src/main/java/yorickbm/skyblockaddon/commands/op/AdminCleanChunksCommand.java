package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * One-shot cleanup of stale entries in islands' {@code modifiedChunks} lists. Iterates each
 * tracked chunk, loads it, and checks whether it contains any non-air content anywhere. Chunks
 * that are entirely air (empty void) get removed from the list.
 *
 * This server runs a void-world overworld, so any non-air block in a chunk is by definition
 * player or mod content - there is no natural terrain to disambiguate from. The check is a
 * trivial "is the chunk empty?" with no false positives and no false negatives.
 *
 * Designed to run once after deploying 9.4+ to clean up the accumulated bloat from the old
 * "track every chunk that ever loaded" behavior. New modifications going forward are tracked
 * precisely via {@link yorickbm.skyblockaddon.events.ChunkEvents}'s BlockEvent handlers.
 *
 * Heavy lifting runs off the server thread; per-chunk scans bounce back to the server thread
 * via {@code server.execute}. Saves the island NBT at the end.
 */
public class AdminCleanChunksCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    /** Timeout for an individual chunk's load attempt during cleanup. */
    private static final int CHUNK_LOAD_TIMEOUT_SECONDS = 30;

    public AdminCleanChunksCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("admin")
                        .requires(s -> s.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Cmds.literal("cleanchunks")
                                .then(Cmds.literal("all")
                                        .executes(ctx -> executeAll(ctx.getSource())))
                                .then(Cmds.argument("uuid", UuidArgument.uuid())
                                        .executes(ctx -> executeOne(ctx.getSource(), UuidArgument.getUuid(ctx, "uuid"))))
                        )
                )
        );
    }

    private int executeAll(final CommandSourceStack src) {
        final List<ForgeIsland> islands = IslandManager.getInstance().getIslands().stream()
                .map(i -> (ForgeIsland) i)
                .toList();
        if (islands.isEmpty()) {
            src.sendFailure(new TextComponent("No islands to clean."));
            return Command.SINGLE_SUCCESS;
        }
        startCleanup(src, islands);
        return Command.SINGLE_SUCCESS;
    }

    private int executeOne(final CommandSourceStack src, final UUID id) {
        final Island island = IslandManager.getInstance().getIslandByUUID(id);
        if (!(island instanceof ForgeIsland forgeIsland)) {
            src.sendFailure(new TextComponent("Island not found: " + id));
            return Command.SINGLE_SUCCESS;
        }
        startCleanup(src, List.of(forgeIsland));
        return Command.SINGLE_SUCCESS;
    }

    private void startCleanup(final CommandSourceStack src, final List<ForgeIsland> islands) {
        final MinecraftServer server = src.getServer();
        final ServerLevel overworld = Objects.requireNonNull(server.getLevel(Level.OVERWORLD));

        src.sendSuccess(new TextComponent(
                "Cleaning chunks for " + islands.size() + " island(s). Empty-void chunks will be pruned. "
                        + "Runs in background; check console for progress.")
                .withStyle(ChatFormatting.YELLOW), false);

        final Thread worker = new Thread(() -> runCleanup(server, overworld, src, islands),
                "SkyblockAddon-CleanChunks-" + UUID.randomUUID());
        worker.setDaemon(true);
        worker.start();
    }

    private static void runCleanup(final MinecraftServer server, final ServerLevel overworld, final CommandSourceStack src,
                                   final List<ForgeIsland> islands) {
        final long startNanos = System.nanoTime();
        int totalChecked = 0;
        int totalPruned = 0;
        int totalKept = 0;
        int totalFailed = 0;

        for (final ForgeIsland island : islands) {
            final List<ChunkPos> snapshot = new ArrayList<>(island.getModifiedChunks());
            final int beforeCount = snapshot.size();
            int prunedThisIsland = 0;
            int failedThisIsland = 0;

            for (final ChunkPos cp : snapshot) {
                totalChecked++;

                final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> loadFuture;
                try {
                    loadFuture = overworld.getChunkSource().getChunkFuture(cp.x, cp.z, ChunkStatus.FULL, true);
                } catch (final Throwable t) {
                    LOGGER.warn("cleanchunks: failed to submit load for chunk {} of island {}", cp, island.getId(), t);
                    failedThisIsland++;
                    continue;
                }

                final ChunkAccess chunk;
                try {
                    final Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> result = loadFuture.get(CHUNK_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    chunk = result.left().orElse(null);
                } catch (final Exception e) {
                    LOGGER.warn("cleanchunks: failed to load chunk {} of island {}", cp, island.getId(), e);
                    failedThisIsland++;
                    continue;
                }
                if (chunk == null) {
                    // Loader returned a failure - treat as "couldn't verify, leave it alone" rather than prune.
                    failedThisIsland++;
                    continue;
                }

                // Bounce the section scan to the server thread - reading chunk internals while a
                // player might be modifying the chunk on the main thread is unsafe off-thread.
                final CompletableFuture<Boolean> scanFuture = new CompletableFuture<>();
                server.execute(() -> {
                    try {
                        scanFuture.complete(chunkHasAnyContent(chunk));
                    } catch (final Throwable t) {
                        scanFuture.completeExceptionally(t);
                    }
                });

                final boolean hasContent;
                try {
                    hasContent = scanFuture.get(10, TimeUnit.SECONDS);
                } catch (final Exception e) {
                    LOGGER.warn("cleanchunks: scan failed for chunk {} of island {}", cp, island.getId(), e);
                    failedThisIsland++;
                    continue;
                }

                if (hasContent) {
                    totalKept++;
                } else {
                    // Remove on server thread to avoid concurrent modification of the island's list.
                    final CompletableFuture<Void> removeFuture = new CompletableFuture<>();
                    server.execute(() -> {
                        try {
                            island.removeChunk(cp);
                            removeFuture.complete(null);
                        } catch (final Throwable t) {
                            removeFuture.completeExceptionally(t);
                        }
                    });
                    try {
                        removeFuture.get(10, TimeUnit.SECONDS);
                        prunedThisIsland++;
                        totalPruned++;
                    } catch (final Exception e) {
                        LOGGER.warn("cleanchunks: failed to prune chunk {} from island {}", cp, island.getId(), e);
                        failedThisIsland++;
                    }
                }
            }

            totalFailed += failedThisIsland;
            LOGGER.info("cleanchunks: island {} - kept {}, pruned {}, failed {} (of {} total)",
                    island.getId(), beforeCount - prunedThisIsland - failedThisIsland, prunedThisIsland, failedThisIsland, beforeCount);
        }

        // Persist the trimmed lists immediately so cleanup survives an unexpected restart.
        final CompletableFuture<Void> saveFuture = new CompletableFuture<>();
        server.execute(() -> {
            try {
                overworld.getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY)
                        .ifPresent(SkyblockAddonWorldCapability::saveIslandsToDisk);
                saveFuture.complete(null);
            } catch (final Throwable t) {
                saveFuture.completeExceptionally(t);
            }
        });
        try {
            saveFuture.get(60, TimeUnit.SECONDS);
        } catch (final Exception e) {
            LOGGER.error("cleanchunks: failed to save islands after cleanup", e);
        }

        final long totalMs = (System.nanoTime() - startNanos) / 1_000_000L;
        final String summary = String.format(
                "cleanchunks complete: %d island(s), %d chunk(s) checked, %d pruned, %d kept, %d failed, in %dms",
                islands.size(), totalChecked, totalPruned, totalKept, totalFailed, totalMs);
        LOGGER.info(summary);
        server.execute(() -> src.sendSuccess(new TextComponent(summary).withStyle(ChatFormatting.GREEN), false));
    }

    /**
     * True iff the chunk has any non-air content anywhere. Trivial check (24 section.hasOnlyAir()
     * calls per chunk) - in a void world there is no natural terrain to disambiguate from, so
     * "non-empty section" === "player or mod content".
     */
    private static boolean chunkHasAnyContent(final ChunkAccess chunk) {
        for (final LevelChunkSection section : chunk.getSections()) {
            if (section != null && !section.hasOnlyAir()) return true;
        }
        return false;
    }
}
