package yorickbm.skyblockaddon.islands;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import yorickbm.skyblockaddon.SkyBlockAddon;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.exceptions.NBTNotFoundException;
import yorickbm.skyblockaddon.util.BuildingBlock;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.util.*;

public class IslandStructurePlacer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;

    public IslandStructurePlacer(final MinecraftServer server) {
        this.server = server;
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public static final class ParsedIslandStructure {
        public final List<BuildingBlock> blocks;
        public final int bigestX;
        public final int bigestZ;

        public ParsedIslandStructure(final List<BuildingBlock> blocks, final int bigestX, final int bigestZ) {
            this.blocks = blocks;
            this.bigestX = bigestX;
            this.bigestZ = bigestZ;
        }
    }

    public static final class IslandReservation {
        public final net.minecraft.core.Vec3i islandLocation;
        public final int height;
        public final net.minecraft.core.Vec3i offset;
        public final Set<ChunkPos> chunks;

        public IslandReservation(final net.minecraft.core.Vec3i islandLocation, final int height, final net.minecraft.core.Vec3i offset, final Set<ChunkPos> chunks) {
            this.islandLocation = islandLocation;
            this.height = height;
            this.offset = offset;
            this.chunks = chunks;
        }
    }

    // ── Pipeline steps ────────────────────────────────────────────────────────

    /**
     * Parse the island structure NBT into placement-ready data.
     * Thread-safe: only reads NBT and builds POJOs.
     */
    public ParsedIslandStructure parseIslandStructure() {
        final long parseStartNanos = System.nanoTime();
        final CompoundTag nbt = SkyBlockAddon.getIslandNBT(server);

        final ListTag paletteNbt = nbt.getList("palette", 10);
        final ListTag blocksNbt = nbt.getList("blocks", 10);

        final ArrayList<BuildingBlock> blocks = new ArrayList<>();
        final ArrayList<BlockState> palette = new ArrayList<>();
        int bigestX = 0, bigestZ = 0;

        for (int i = 0; i < paletteNbt.size(); i++) palette.add(NbtUtils.readBlockState(paletteNbt.getCompound(i)));
        for (int i = 0; i < blocksNbt.size(); i++) {
            final CompoundTag blockNbt = blocksNbt.getCompound(i);
            final ListTag blockPosNbt = blockNbt.getList("pos", 3);
            if (blockPosNbt.size() < 3) {
                LOGGER.warn("Island structure: skipping block entry with invalid pos array (size={})", blockPosNbt.size());
                continue;
            }

            if (blockPosNbt.getInt(0) > bigestX) bigestX = blockPosNbt.getInt(0);
            if (blockPosNbt.getInt(2) > bigestZ) bigestZ = blockPosNbt.getInt(2);

            blocks.add(new BuildingBlock(
                    new BlockPos(blockPosNbt.getInt(0), blockPosNbt.getInt(1), blockPosNbt.getInt(2)),
                    palette.get(blockNbt.getInt("state"))
            ));
        }

        if (blocks.isEmpty()) throw new NBTNotFoundException();

        final long parseMs = (System.nanoTime() - parseStartNanos) / 1_000_000L;
        LOGGER.info("Island gen: parsed structure NBT in {}ms ({} blocks, {} palette entries)", parseMs, blocks.size(), palette.size());

        return new ParsedIslandStructure(blocks, bigestX, bigestZ);
    }

    /**
     * Server-thread only. Reserves the next grid slot and pre-computes the chunk set the
     * structure will touch. Pre-load those chunks off-thread before calling
     * {@link #placeReservedIsland}.
     */
    public IslandReservation reserveIslandLocation(final ParsedIslandStructure parsed) {
        final net.minecraft.core.Vec3i islandLocation = ForgeConverter.InternalToForgeVec3i(IslandManager.getInstance().getNextIslandGen());
        final int height = Integer.parseInt(SkyblockAddonConfig.getForKey("island.spawn.height"));
        final net.minecraft.core.Vec3i offset = islandLocation.offset(-(parsed.bigestX / 2), height, -(parsed.bigestZ / 2));

        final Set<ChunkPos> chunks = new HashSet<>();
        for (final BuildingBlock building : parsed.blocks) {
            if (building.getState().isAir()) continue;
            chunks.add(new ChunkPos(building.getPos().offset(offset)));
        }
        chunks.add(new ChunkPos(new BlockPos(islandLocation.getX(), height, islandLocation.getZ())));

        return new IslandReservation(islandLocation, height, offset, chunks);
    }

    /**
     * Place a parsed structure into the world using a pre-reserved slot. Must run on the
     * server thread. Writes directly to {@link ChunkAccess#setBlockState} (no neighbor
     * updates) and sends one batched chunk packet per touched chunk at the end.
     */
    public net.minecraft.core.Vec3i placeReservedIsland(final ServerLevel level, final ParsedIslandStructure parsed, final IslandReservation reservation) {
        final long placeStartNanos = System.nanoTime();

        int placedCount = 0;
        final HashMap<ChunkPos, ChunkAccess> touchedChunks = new HashMap<>();
        for (final BuildingBlock building : parsed.blocks) {
            if (building.getState().isAir()) continue;
            final BlockPos pos = building.getPos().offset(reservation.offset);
            final ChunkAccess chunk = touchedChunks.computeIfAbsent(
                    new ChunkPos(pos),
                    cp -> level.getChunk(cp.x, cp.z, ChunkStatus.FULL, true));
            chunk.setBlockState(pos, building.getState(), false);
            placedCount++;
        }
        final long writeMs = (System.nanoTime() - placeStartNanos) / 1_000_000L;

        final long packetStartNanos = System.nanoTime();
        final ServerChunkCache chunkSource = level.getChunkSource();
        touchedChunks.forEach((cp, chunk) -> {
            if (!(chunk instanceof LevelChunk levelChunk)) return;
            chunkSource.chunkMap.getPlayers(cp, false).forEach(player ->
                    ((ServerGamePacketListenerImpl) player.connection).send(
                            new ClientboundLevelChunkWithLightPacket(levelChunk, level.getLightEngine(), null, null, false))
            );
        });
        final long packetMs = (System.nanoTime() - packetStartNanos) / 1_000_000L;

        final ChunkAccess heightChunk = level.getChunk(new BlockPos(reservation.islandLocation.getX(), reservation.height, reservation.islandLocation.getZ()));
        // LevelChunk#setBlockState updates WORLD_SURFACE at runtime, not the world-generation-only heightmap.
        final int topHeight = heightChunk.getHeight(
                Heightmap.Types.WORLD_SURFACE,
                reservation.islandLocation.getX(),
                reservation.islandLocation.getZ()
        ) + 2;

        LOGGER.info("Island gen: placed {} blocks across {} chunk(s) at {} in {}ms (chunk packets: {}ms)",
                placedCount, touchedChunks.size(), reservation.islandLocation, writeMs, packetMs);

        return new net.minecraft.core.Vec3i(reservation.islandLocation.getX(), topHeight, reservation.islandLocation.getZ());
    }

    /**
     * Convenience: reserves a slot AND places in one synchronous call. Prefer
     * {@link #reserveIslandLocation} + off-thread chunk preload + {@link #placeReservedIsland}
     * for performance.
     */
    public net.minecraft.core.Vec3i placeIslandStructure(final ServerLevel level, final ParsedIslandStructure parsed) {
        return placeReservedIsland(level, parsed, reserveIslandLocation(parsed));
    }

    /**
     * Full pipeline convenience: parse + reserve + place in one call. Prefer splitting the
     * parse off-thread when possible.
     */
    public net.minecraft.core.Vec3i genIsland(final ServerLevel level) {
        return placeIslandStructure(level, parseIslandStructure());
    }
}
