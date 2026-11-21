package yorickbm.skyblockaddon.islands;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability.IslandReservation;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability.ParsedIslandStructure;
import yorickbm.skyblockaddon.util.BuildingBlock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Places a parsed island structure into its reserved slot a batch at a time. Server thread only,
 * and every touched chunk must already be loaded (the creation queue holds a ticket on them).
 * Writes go straight to the chunk (no neighbour updates, no per-block packets); each placed block
 * queues a light check, and one chunk packet per touched chunk is sent when placement is done.
 */
public final class IslandStructurePlacement {
    private final List<BuildingBlock> solidBlocks;
    private final IslandReservation reservation;
    private final Map<ChunkPos, LevelChunk> touchedChunks = new HashMap<>();
    private int nextBlockIndex;
    private int batchCount;
    private long placementNanos;

    public IslandStructurePlacement(final ParsedIslandStructure parsedStructure, final IslandReservation reservation) {
        this.solidBlocks = parsedStructure.blocks.stream().filter(block -> !block.getState().isAir()).toList();
        this.reservation = reservation;
    }

    public boolean placeNextBatch(final ServerLevel level, final int blockBudget) {
        final long batchStartNanos = System.nanoTime();
        final ThreadedLevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        final int batchEnd = (int) Math.min((long) nextBlockIndex + blockBudget, solidBlocks.size());
        for (; nextBlockIndex < batchEnd; nextBlockIndex++) {
            final BuildingBlock block = solidBlocks.get(nextBlockIndex);
            final BlockPos position = block.getPos().offset(reservation.offset);
            final LevelChunk chunk = touchedChunks.computeIfAbsent(new ChunkPos(position), chunkPos -> level.getChunk(chunkPos.x, chunkPos.z));
            chunk.setBlockState(position, block.getState(), false);
            lightEngine.checkBlock(position);
        }
        batchCount++;
        placementNanos += System.nanoTime() - batchStartNanos;
        return isFinished();
    }

    public boolean isFinished() {
        return nextBlockIndex >= solidBlocks.size();
    }

    public void sendChunksToTrackingPlayers(final ServerLevel level) {
        touchedChunks.forEach((chunkPos, chunk) -> level.getChunkSource().chunkMap.getPlayers(chunkPos, false).forEach(player ->
                player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null, false))));
    }

    public Vec3i findSpawnPosition(final ServerLevel level) {
        final Vec3i slotCenter = reservation.islandLocation;
        final LevelChunk centerChunk = level.getChunk(slotCenter.getX() >> 4, slotCenter.getZ() >> 4);
        final int spawnHeight = centerChunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, slotCenter.getX(), slotCenter.getZ()) + 2;
        return new Vec3i(slotCenter.getX(), spawnHeight, slotCenter.getZ());
    }

    public int getProgressPercent() {
        return solidBlocks.isEmpty() ? 100 : (int) ((long) nextBlockIndex * 100 / solidBlocks.size());
    }

    public int getPlacedBlockCount() {
        return nextBlockIndex;
    }

    public int getTouchedChunkCount() {
        return touchedChunks.size();
    }

    public int getBatchCount() {
        return batchCount;
    }

    public long getPlacementMillis() {
        return placementNanos / 1_000_000L;
    }
}
