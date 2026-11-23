package yorickbm.skyblockaddon.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.ProgressBar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class IslandChunkManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int CHUNK_SIZE = 16;
    private static final TicketType<ChunkPos> PURGE_TICKET = TicketType.create("skyblockaddon_purge", Comparator.comparingLong(ChunkPos::toLong));
    private static final int FULL_CHUNK_TICKET_DISTANCE = 0;

    public static void createDummies(List<ForgeIsland> islands, ProgressBar bar, Predicate<ForgeIsland> islandStillPurgeable, Consumer<ForgeIsland> onIslandFinished, Runnable onDone, ServerLevel level) {
        Task tracker = new Task(onDone, bar);
        tracker.proposeSubTask();
        bar.sendToast(new TextComponent("Loading " + islands.size() + " island(s)..."));

        for(ForgeIsland island : islands) {
            if(island.getModifiedChunks().isEmpty()) continue;

            Task task = new Task(() -> {
                onIslandFinished.accept(island);
                tracker.completeSubTask(false);
            }, bar);
            tracker.addSubTask();
            LOGGER.debug("Created task {} for island {} ({})", task.getId(), island.getId(), island.getModifiedChunks().size());

            task.proposeSubTask();
            for (ChunkPos chunkPos : island.getModifiedChunks()) {
                task.addSubTask();
                requestChunkLoad(level, chunkPos, task, () -> islandStillPurgeable.test(island));
            }
            task.completeSubTask(true);
        }
        tracker.completeSubTask(true);
    }

    public static void requestChunkLoad(ServerLevel level, ChunkPos pos, Task task, BooleanSupplier clearingAllowed) {
        ChunkTaskScheduler.queue(() -> {
            if (!clearingAllowed.getAsBoolean()) {
                task.completeSubTask(false);
                return;
            }
            task.getBar().sendToast(new TextComponent("Loading chunk " + pos + ""));
            level.getChunkSource().addRegionTicket(PURGE_TICKET, pos, FULL_CHUNK_TICKET_DISTANCE, pos);
            ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
            processChunk(chunk, level, task, clearingAllowed);
        });
    }

    public static void processChunk(ChunkAccess chunk, ServerLevel level, Task task, BooleanSupplier clearingAllowed) {
        new Thread(() -> {

            LOGGER.debug("Processing chunk {} for task {}", chunk.getPos(), task.getId());
            List<BlockPos> nonAirBlocks = new ArrayList<>();

            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight();

            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int y = maxY; y >= minY; y--) {
                        BlockPos pos = new BlockPos(
                                x + chunk.getPos().getMinBlockX(),
                                y,
                                z + chunk.getPos().getMinBlockZ()
                        );
                        if (!chunk.getBlockState(pos).isAir()) {
                            nonAirBlocks.add(pos);
                        }
                    }
                }
            }

            if (nonAirBlocks.isEmpty()) {
                level.getServer().execute(() -> {
                    releasePurgeTicket(level, chunk.getPos());
                    task.completeSubTask(false);
                });
                LOGGER.debug("Finished processing (empty) chunk {} for task {}", chunk.getPos(), task.getId());
                return;
            }

            // Now schedule block removals in batches of 100
            int batchSize = Integer.parseInt(SkyblockAddonConfig.getForKey("purge.blocks"));
            for (int i = 0; i < nonAirBlocks.size(); i += batchSize) {
                final int start = i;
                final int end = Math.min(i + batchSize, nonAirBlocks.size());

                ChunkTaskScheduler.queue(() -> {
                    if (clearingAllowed.getAsBoolean()) {
                        for (int j = start; j < end; j++) {
                            BlockPos pos = nonAirBlocks.get(j);
                            removeBlockEntityBeforeClearing(chunk, pos);
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false); // silent
                        }
                    }

                    // After last batch, send chunk update to players
                    if (end >= nonAirBlocks.size()) {
                        level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)
                                .forEach(player -> player.connection
                                        .send(new ClientboundLevelChunkWithLightPacket((LevelChunk) chunk, level.getLightEngine(), null, null, false)));
                        releasePurgeTicket(level, chunk.getPos());

                        task.completeSubTask(false);
                        task.getBar().sendToast(new TextComponent("Cleared chunk " + chunk.getPos() + ""));
                    }
                });
            }
        }, "AsyncChunkScan-" + chunk.getPos().x + "," + chunk.getPos().z).start();
    }

    private static void releasePurgeTicket(ServerLevel level, ChunkPos pos) {
        level.getChunkSource().removeRegionTicket(PURGE_TICKET, pos, FULL_CHUNK_TICKET_DISTANCE, pos);
    }

    /**
     * Purged blocks must not drop anything: without a block entity, onRemove finds nothing to spill or pack (a packed
     * Sophisticated barrel never despawns and leaves an entry in sophisticatedstorage.dat). getBlockEntity first so a
     * block entity still pending as chunk NBT is promoted and removed instead of being loaded again during onRemove.
     */
    private static void removeBlockEntityBeforeClearing(ChunkAccess chunk, BlockPos pos) {
        if (chunk.getBlockEntity(pos) != null) {
            chunk.removeBlockEntity(pos);
        }
    }

    public static class Task {
        private final UUID id = UUID.randomUUID();
        private final AtomicInteger subTasks = new AtomicInteger(0);
        private volatile boolean completed = false;
        private final Runnable onFinished;

        private final ProgressBar bar;

        public UUID getId() {
            return id;
        }

        public Task(Runnable onFinished, ProgressBar bar) {
            this.onFinished = onFinished;
            this.bar = bar;
        }


        public int proposeSubTask() {
            return subTasks.incrementAndGet();
        }
        public int addSubTask() {
            bar.addTask();
            return proposeSubTask();
        }
        public int completeSubTask(boolean isProposed) {
            int remaining = subTasks.decrementAndGet();
            if(!isProposed) bar.completeTask();

            if (remaining == 0 && !completed) {
                completed = true;
                if(this.onFinished != null) onFinished.run();
            }
            return remaining;
        }

        public ProgressBar getBar() {
            return this.bar;
        }
    }
}