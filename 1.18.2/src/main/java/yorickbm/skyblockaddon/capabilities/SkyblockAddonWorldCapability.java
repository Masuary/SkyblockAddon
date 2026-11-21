package yorickbm.skyblockaddon.capabilities;

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
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import yorickbm.skyblockaddon.SkyBlockAddon;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.exceptions.NBTNotFoundException;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.legacy.LegacyFormatter;
import yorickbm.skyblockaddon.util.BuildingBlock;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.NBTEncoder;
import yorickbm.skyblockaddon.util.NBTUtil;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SkyblockAddonWorldCapability {
    private static final Logger LOGGER = LogUtils.getLogger();
    MinecraftServer serverInstance;

    public SkyblockAddonWorldCapability(final MinecraftServer server) {
        serverInstance = server;

        //Init Island Manager
        IslandManager.getInstance().initializeCaches(server.getMaxPlayers());
    }

    /**
     * Save data into NBT.
     */
    public void saveNBTData(final CompoundTag nbt) {
        nbt.put("lastIsland", NBTUtil.Vec3iToNBT(IslandManager.getInstance().getLastLocation()));
        nbt.putInt("nbt-v", 6);

        ListTag listTag = new ListTag();
        for (Vec3i vec : IslandManager.getInstance().getReusableLocations()) {
            CompoundTag vecTag = new CompoundTag();
            vecTag.putInt("x", vec.getX());
            vecTag.putInt("y", vec.getY());
            vecTag.putInt("z", vec.getZ());
            listTag.add(vecTag);
        }
        nbt.put("reusableLocations", listTag);

        saveIslandsToDisk();
    }

    /**
     * Persist every in-memory island to its NBT file. Single source of truth for island-file
     * writes - both the capability serialize hook and {@code WorldEvent.Save} call this so the
     * two save paths can't diverge on which islands get written or how they're serialised.
     * Must run on the server thread (reads {@link IslandManager} singleton state).
     */
    public void saveIslandsToDisk() {
        final Path worldPath = serverInstance.getWorldPath(LevelResource.ROOT).normalize();
        final Path filePath = worldPath.resolve("islanddata");

        NBTEncoder.saveToFile(IslandManager.getInstance().getIslands().stream()
                .map(ForgeIsland::new) // constructor copies data from Island
                .collect(Collectors.toList()), filePath);
    }

    /**
     * Load data from NBT.
     */
    public void loadNBTData(final CompoundTag nbt) {
        Vec3i lastLocation = NBTUtil.NBTToVec3i(nbt.getCompound("lastIsland"));
        List<Vec3i> reusableLocations = new ArrayList<>();

        final Path worldPath = serverInstance.getWorldPath(LevelResource.ROOT).normalize();
        final Path filePath = worldPath.resolve("islanddata");

        if (nbt.contains("reusableLocations", 9)) { // 9 = ListTag type
            ListTag listTag = nbt.getList("reusableLocations", 10); // 10 = CompoundTag type
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag vecTag = listTag.getCompound(i);
                int x = vecTag.getInt("x");
                int y = vecTag.getInt("y");
                int z = vecTag.getInt("z");
                reusableLocations.add(new Vec3i(x, y, z));
            }
        }

        //legacy check
        if(nbt.contains("nbt-v") && nbt.getInt("nbt-v") < 5) {
            LOGGER.info("Converted {} island(s).", LegacyFormatter.formatLegacy(nbt, filePath));
        }
        if(nbt.contains("nbt-v") && nbt.getInt("nbt-v") == 5) {
            LOGGER.info("Converted {} island(s).", LegacyFormatter.formatBeta(filePath));
        }

        final Collection<ForgeIsland> islands = NBTEncoder.loadFromFolder(filePath, ForgeIsland.class);
        IslandManager.getInstance().initializeData(islands.stream().toList(), reusableLocations, lastLocation);
        LOGGER.info("Loaded {} island(s).", islands.size());
    }

    /**
     * Parsed structure data ready for placement.
     * Pure data - safe to construct on any thread; consumed only on the server thread.
     */
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

    /**
     * Parse the island structure NBT into placement-ready data.
     * Thread-safe: only reads the structure NBT and builds POJOs - touches no world or singleton state.
     */
    public ParsedIslandStructure parseIslandStructure() {
        final long parseStartNanos = System.nanoTime();
        final CompoundTag nbt = SkyBlockAddon.getIslandNBT(serverInstance);

        final ListTag paletteNbt = nbt.getList("palette", 10);
        final ListTag blocksNbt = nbt.getList("blocks", 10);

        final ArrayList<BuildingBlock> blocks = new ArrayList<>();
        final ArrayList<BlockState> palette = new ArrayList<>();
        int bigestX = 0, bigestZ = 0;

        for (int i = 0; i < paletteNbt.size(); i++) palette.add(NbtUtils.readBlockState(paletteNbt.getCompound(i)));
        for (int i = 0; i < blocksNbt.size(); i++) {
            final CompoundTag blockNbt = blocksNbt.getCompound(i);
            final ListTag blockPosNbt = blockNbt.getList("pos", 3);

            if (blockPosNbt.getInt(0) > bigestX) bigestX = blockPosNbt.getInt(0);
            if (blockPosNbt.getInt(2) > bigestZ) bigestZ = blockPosNbt.getInt(2);

            blocks.add(new BuildingBlock(
                    new BlockPos(
                            blockPosNbt.getInt(0),
                            blockPosNbt.getInt(1),
                            blockPosNbt.getInt(2)
                    ),
                    palette.get(blockNbt.getInt("state"))
            ));
        }

        if (blocks.isEmpty()) {
            throw new NBTNotFoundException();
        }

        final long parseMs = (System.nanoTime() - parseStartNanos) / 1_000_000L;
        LOGGER.info("Island gen: parsed structure NBT in {}ms ({} blocks, {} palette entries)", parseMs, blocks.size(), palette.size());

        return new ParsedIslandStructure(blocks, bigestX, bigestZ);
    }

    /**
     * A reserved island slot plus the chunk set the structure will touch. Returned by
     * {@link #reserveIslandLocation}; consumed by {@link #placeReservedIsland}. Held by the
     * IslandCreateCommand worker thread between the two so chunks can be pre-loaded off the
     * server tick.
     */
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

    /**
     * Server-thread only. Reserves the next grid slot via {@link IslandManager#getNextIslandGen()}
     * and pre-computes the set of chunks the structure will touch. The caller is expected to
     * pre-load those chunks (off-thread) and then call {@link #placeReservedIsland} on the server
     * thread.
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
        //Also include the chunk we read the spawn heightmap from.
        chunks.add(new ChunkPos(new BlockPos(islandLocation.getX(), height, islandLocation.getZ())));

        return new IslandReservation(islandLocation, height, offset, chunks);
    }

    /**
     * Place a parsed structure into the world using a pre-reserved slot. Must run on the server
     * thread. Pairs with {@link #reserveIslandLocation}.
     *
     * Performance: writes directly to {@link ChunkAccess#setBlockState} (no neighbor updates, no
     * per-block packets) and sends one batched chunk packet per touched chunk at the end. This is
     * the same pattern used by AdminPurgeCommand's chunk clear path. On a modded server with
     * pervasive block-update listeners, a 16k-block island used to take minutes via
     * {@code setBlockAndUpdate}; the direct-chunk approach completes well inside a single tick,
     * provided the chunks were pre-loaded so {@code level.getChunk} doesn't trigger worldgen.
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
        final int topHeight = heightChunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, reservation.islandLocation.getX(), reservation.islandLocation.getZ()) + 2;

        LOGGER.info("Island gen: placed {} blocks across {} chunk(s) at {} in {}ms (chunk packets: {}ms)",
                placedCount, touchedChunks.size(), reservation.islandLocation, writeMs, packetMs);

        return new net.minecraft.core.Vec3i(reservation.islandLocation.getX(), topHeight, reservation.islandLocation.getZ());
    }

    /**
     * Server-thread convenience wrapper: reserves a slot AND places in one synchronous call.
     * Triggers worldgen on the server tick if the touched chunks are not yet loaded. Prefer
     * {@link #reserveIslandLocation} + off-thread chunk preload + {@link #placeReservedIsland}
     * for performance.
     */
    public net.minecraft.core.Vec3i placeIslandStructure(final ServerLevel level, final ParsedIslandStructure parsed) {
        return placeReservedIsland(level, parsed, reserveIslandLocation(parsed));
    }

    /**
     * Server-thread-only convenience wrapper. Prefer {@link #parseIslandStructure()} +
     * {@link #placeIslandStructure(ServerLevel, ParsedIslandStructure)} when you can do the
     * parse off-thread.
     */
    public net.minecraft.core.Vec3i genIsland(final ServerLevel level) {
        return placeIslandStructure(level, parseIslandStructure());
    }

    public void removeIslandNBT(ForgeIsland data) {
        final Path worldPath = serverInstance.getWorldPath(LevelResource.ROOT).normalize();
        final Path filePath = worldPath.resolve("islanddata");

        NBTEncoder.removeFileFromFolder(filePath, data);
    }

    public List<UUID> getPurgableIslands() {
        return IslandManager.getInstance().getEntrySet().entrySet().stream() // Create a stream of map entries
                .filter(entry -> entry.getValue().isAbandoned() && !((ForgeIsland)entry.getValue()).getModifiedChunks().isEmpty()) // Filter entries where island is abandoned
                .map(Map.Entry::getKey) // Map to the UUID (key) of the entry
                .collect(Collectors.toList()); // Collect into a List<UUID>
    }
}
