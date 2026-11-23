package yorickbm.skyblockaddon.capabilities;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import yorickbm.skyblockaddon.SkyBlockAddon;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.IslandSlot;
import yorickbm.skyblockaddon.core.islands.IslandSlotAllocator;
import yorickbm.skyblockaddon.core.util.IslandFileWriteQueue;
import yorickbm.skyblockaddon.core.util.IslandFileWriteTracker;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.legacy.LegacyFormatter;
import yorickbm.skyblockaddon.util.BuildingBlock;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.NBTEncoder;
import yorickbm.skyblockaddon.util.NBTUtil;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class SkyblockAddonWorldCapability {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESERVED_SLOTS_TAG = "reservedSlots";
    MinecraftServer serverInstance;
    private Vec3i persistedNextFreshSlot = new Vec3i(0, 0, 0);
    private List<Vec3i> persistedReusableSlots = List.of();
    private List<IslandSlot> persistedAdminReservedSlots = List.of();
    private boolean islandsLoaded = false;
    private static final Duration STOP_DRAIN_TIMEOUT = Duration.ofMinutes(5);
    private static volatile IslandFileWriteQueue activeIslandFileWriteQueue;
    private final IslandFileWriteTracker islandFileWriteTracker = new IslandFileWriteTracker();
    private final IslandFileWriteQueue islandFileWriteQueue;

    public SkyblockAddonWorldCapability(final MinecraftServer server) {
        serverInstance = server;
        this.islandFileWriteQueue = new IslandFileWriteQueue(islandDataFolder(), this.islandFileWriteTracker, SkyblockAddonWorldCapability::logWrittenBatch,
                taskFailure -> LOGGER.error("An island writer task failed; island files queued in it are written again at the next save", taskFailure));
        activeIslandFileWriteQueue = this.islandFileWriteQueue;

        //Init Island Manager
        IslandManager.getInstance().initializeCaches(server.getMaxPlayers());
    }

    /**
     * Save the island grid state into capabilities.dat. Until the islands are loaded the allocator holds nothing real
     * (a failed load stops the server, which still saves on the way down), so the state read at start is written back
     * unchanged instead.
     */
    public void saveNBTData(final CompoundTag nbt) {
        if (!this.islandsLoaded) {
            LOGGER.warn("Islands are not loaded; writing back the island grid state read at start unchanged.");
            writeGridState(nbt, this.persistedNextFreshSlot, this.persistedReusableSlots, this.persistedAdminReservedSlots);
            return;
        }
        writeGridState(nbt, IslandManager.getInstance().getNextFreshSlot(), IslandManager.getInstance().getReusableLocations(),
                IslandManager.getInstance().getAdminReservedSlots());
    }

    private static void writeGridState(final CompoundTag nbt, final Vec3i nextFreshSlot, final Collection<Vec3i> reusableSlots,
                                       final Collection<IslandSlot> adminReservedSlots) {
        nbt.put("lastIsland", NBTUtil.vec3iToNBT(nextFreshSlot));
        nbt.putInt("nbt-v", 6);

        ListTag listTag = new ListTag();
        for (Vec3i vec : reusableSlots) {
            CompoundTag vecTag = new CompoundTag();
            vecTag.putInt("x", vec.getX());
            vecTag.putInt("y", vec.getY());
            vecTag.putInt("z", vec.getZ());
            listTag.add(vecTag);
        }
        nbt.put("reusableLocations", listTag);

        final ListTag reservedSlotsTag = new ListTag();
        for (final IslandSlot reservedSlot : adminReservedSlots) {
            final CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("x", reservedSlot.x());
            slotTag.putInt("z", reservedSlot.z());
            reservedSlotsTag.add(slotTag);
        }
        nbt.put(RESERVED_SLOTS_TAG, reservedSlotsTag);
    }

    /**
     * Persist the in-memory islands that changed since their last write. Island files are written only through here:
     * the overworld's {@code WorldEvent.Save} (autosave, save-all, stop) and the admin commands that change many islands
     * call it; the capabilities.dat hook stores the grid state only. The live island objects are serialized here
     * on the server thread, where islands are changed; the bytes go to the island writer thread, which does the disk
     * work in queue order (the server stop waits for it in {@link #drainIslandWritesOnServerStop}).
     */
    public void saveIslandsToDisk() {
        if (!serverInstance.isSameThread()) {
            throw new IllegalStateException("Islands are saved from the live objects and must be saved on the server thread, not on " + Thread.currentThread().getName());
        }
        if (!this.islandsLoaded) {
            LOGGER.warn("Islands are not loaded; skipping the island file save so no island file is touched.");
            return;
        }
        final long serializeStartNanos = System.nanoTime();
        final NBTEncoder.IslandSerializationReport report = NBTEncoder.serializeChanged(IslandManager.getInstance().getIslands().stream()
                .map(SkyblockAddonWorldCapability::asForgeIsland)
                .toList(), this.islandFileWriteTracker);
        this.islandFileWriteQueue.queueWrites(report.changed());
        final long serializeMillis = (System.nanoTime() - serializeStartNanos) / 1_000_000L;
        LOGGER.debug("Serialized islands in {} ms: {} queued for writing, {} unchanged, {} failed.",
                serializeMillis, report.changed().size(), report.unchanged(), report.failed().size());
    }

    private static void logWrittenBatch(final IslandFileWriteQueue.BatchReport report) {
        report.failures().forEach((islandId, failure) -> LOGGER.error("Failed to write the file of island {}; it is written again at the next save", islandId, failure));
        if (report.failures().isEmpty()) {
            LOGGER.info("Wrote {} island file(s) in {} ms ({} skipped for a newer queued version).", report.written(), report.millis(), report.superseded());
        } else {
            LOGGER.error("Wrote {} island file(s) in {} ms ({} skipped for a newer queued version); {} failed: {}", report.written(), report.millis(),
                    report.superseded(), report.failures().size(), report.failures().keySet());
        }
    }

    /**
     * Wait until every island write and delete queued so far is on disk, for code that reads island files back.
     *
     * @throws TimeoutException when the island writer is still busy at the timeout
     */
    public void awaitQueuedIslandWrites(final Duration timeout) throws TimeoutException, InterruptedException {
        this.islandFileWriteQueue.awaitQueued(timeout);
    }

    /**
     * Called when the server has stopped, after its final save: waits for the island writer to finish so no queued
     * island file is lost when the JVM exits.
     */
    public static void drainIslandWritesOnServerStop() {
        final IslandFileWriteQueue queue = activeIslandFileWriteQueue;
        if (queue == null) return;
        activeIslandFileWriteQueue = null;
        try {
            if (!queue.shutdownAndDrain(STOP_DRAIN_TIMEOUT)) {
                LOGGER.error("The island writer did not finish within {} s of the server stop; island files queued last may not be on disk.", STOP_DRAIN_TIMEOUT.toSeconds());
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while waiting for the island writer at the server stop; island files queued last may not be on disk.", interrupted);
        }
    }

    private static ForgeIsland asForgeIsland(final Island island) {
        if (island instanceof final ForgeIsland forgeIsland) return forgeIsland;
        throw new IllegalStateException("Island " + island.getId() + " is a " + island.getClass().getName() + ", not a ForgeIsland, and cannot be saved");
    }

    /**
     * Read the island grid state kept in {@code capabilities.dat} (next fresh slot, reusable and reserved slots) and
     * convert legacy island data. The islands themselves are loaded by {@link #loadIslandsFromDisk()}, which also runs
     * when this file is missing or unreadable.
     */
    public void loadNBTData(final CompoundTag nbt) {
        Vec3i lastLocation = NBTUtil.nbtToVec3i(nbt.getCompound("lastIsland"));
        List<Vec3i> reusableLocations = new ArrayList<>();

        final Path filePath = islandDataFolder();

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

        final List<IslandSlot> adminReservedSlots = new ArrayList<>();
        final ListTag reservedSlotsTag = nbt.getList(RESERVED_SLOTS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < reservedSlotsTag.size(); index++) {
            final CompoundTag slotTag = reservedSlotsTag.getCompound(index);
            adminReservedSlots.add(new IslandSlot(slotTag.getInt("x"), slotTag.getInt("z")));
        }

        //legacy check
        if(nbt.contains("nbt-v") && nbt.getInt("nbt-v") < 5) {
            LOGGER.info("Converted {} island(s).", LegacyFormatter.formatLegacy(nbt, filePath));
        }
        if(nbt.contains("nbt-v") && nbt.getInt("nbt-v") == 5) {
            LOGGER.info("Converted {} island(s).", LegacyFormatter.formatBeta(filePath));
        }

        this.persistedNextFreshSlot = lastLocation;
        this.persistedReusableSlots = List.copyOf(reusableLocations);
        this.persistedAdminReservedSlots = List.copyOf(adminReservedSlots);
        LOGGER.info("Read the island grid state: next fresh slot {}, {} reusable slot(s), {} admin reserved slot(s).",
                lastLocation, reusableLocations.size(), adminReservedSlots.size());
    }

    /**
     * Load every island file in {@code islanddata} into {@link IslandManager} together with the grid state read from
     * {@code capabilities.dat} (defaults when that file was missing). Runs once per server start, when the overworld
     * loads and before any chunk or player.
     */
    public void loadIslandsFromDisk() {
        if (this.islandsLoaded) {
            throw new IllegalStateException("Islands were already loaded for this world; refusing to load them twice");
        }
        final Path islandDataFolder = islandDataFolder();
        final Collection<ForgeIsland> islands = NBTEncoder.loadFromFolder(islandDataFolder, ForgeIsland.class);
        IslandManager.getInstance().initializeData(List.copyOf(islands), this.persistedReusableSlots, this.persistedNextFreshSlot, this.persistedAdminReservedSlots);
        this.islandsLoaded = true;
        LOGGER.info("Loaded {} island(s) from '{}', {} admin reserved slot(s).", islands.size(), islandDataFolder, this.persistedAdminReservedSlots.size());
    }

    private Path islandDataFolder() {
        return serverInstance.getWorldPath(LevelResource.ROOT).normalize().resolve("islanddata");
    }

    /**
     * Parsed structure data ready for placement, built once per server start and shared by every island creation.
     * Immutable: the block list cannot change and its positions and block states are immutable.
     */
    public static final class ParsedIslandStructure {
        public final List<BuildingBlock> blocks;
        public final int biggestX;
        public final int biggestZ;
        public final long parseMillis;

        public ParsedIslandStructure(final List<BuildingBlock> blocks, final int biggestX, final int biggestZ, final long parseMillis) {
            this.blocks = List.copyOf(blocks);
            this.biggestX = biggestX;
            this.biggestZ = biggestZ;
            this.parseMillis = parseMillis;
        }

        /**
         * Convert structure NBT into placement-ready data.
         * @throws IllegalStateException - When the structure has no blocks or palette, or a block has a broken position or palette index
         */
        public static ParsedIslandStructure parse(final CompoundTag nbt) {
            final long parseStartNanos = System.nanoTime();
            final ListTag paletteNbt = nbt.getList("palette", Tag.TAG_COMPOUND);
            final ListTag blocksNbt = nbt.getList("blocks", Tag.TAG_COMPOUND);
            if (paletteNbt.isEmpty()) throw new IllegalStateException("the structure has no palette");
            if (blocksNbt.isEmpty()) throw new IllegalStateException("the structure has no blocks");

            final List<BlockState> palette = new ArrayList<>();
            for (int i = 0; i < paletteNbt.size(); i++) palette.add(NbtUtils.readBlockState(paletteNbt.getCompound(i)));

            final List<BuildingBlock> blocks = new ArrayList<>();
            int biggestX = 0, biggestZ = 0;
            for (int i = 0; i < blocksNbt.size(); i++) {
                final BuildingBlock block = parseBlock(blocksNbt.getCompound(i), i, palette);
                biggestX = Math.max(biggestX, block.getPos().getX());
                biggestZ = Math.max(biggestZ, block.getPos().getZ());
                blocks.add(block);
            }

            final long parseMillis = (System.nanoTime() - parseStartNanos) / 1_000_000L;
            return new ParsedIslandStructure(blocks, biggestX, biggestZ, parseMillis);
        }

        private static BuildingBlock parseBlock(final CompoundTag blockNbt, final int blockIndex, final List<BlockState> palette) {
            final ListTag blockPosNbt = blockNbt.getList("pos", Tag.TAG_INT);
            if (blockPosNbt.size() != 3) {
                throw new IllegalStateException("block " + blockIndex + " has a position with " + blockPosNbt.size() + " coordinates instead of 3");
            }
            final int paletteIndex = blockNbt.getInt("state");
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                throw new IllegalStateException("block " + blockIndex + " uses palette entry " + paletteIndex + ", but the palette has " + palette.size() + " entries");
            }
            return new BuildingBlock(new BlockPos(blockPosNbt.getInt(0), blockPosNbt.getInt(1), blockPosNbt.getInt(2)), palette.get(paletteIndex));
        }
    }

    /**
     * The island structure parsed when the server started.
     */
    public ParsedIslandStructure parseIslandStructure() {
        return SkyBlockAddon.getIslandStructure();
    }

    /**
     * A reserved island slot plus the chunk set the structure will touch. Returned by
     * {@link #reserveIslandLocation}; the island creation queue keeps those chunks loaded with a
     * ticket and places the structure into them over several ticks.
     */
    public static final class IslandReservation {
        public final net.minecraft.core.Vec3i islandLocation;
        public final int height;
        public final net.minecraft.core.Vec3i offset;
        public final Set<ChunkPos> chunks;
        public final List<IslandSlot> skippedSlots;

        public IslandReservation(final net.minecraft.core.Vec3i islandLocation, final int height, final net.minecraft.core.Vec3i offset, final Set<ChunkPos> chunks, final List<IslandSlot> skippedSlots) {
            this.islandLocation = islandLocation;
            this.height = height;
            this.offset = offset;
            this.chunks = chunks;
            this.skippedSlots = skippedSlots;
        }

        public Vec3i slotCenter() {
            return ForgeConverter.forgeToInternalVec3i(islandLocation);
        }
    }

    /**
     * Takes the next free grid slot (reserved and occupied slots are skipped) and computes the
     * chunks the structure will touch. The caller owns the slot from here on and must either
     * register an island on it or return it with {@link IslandManager#islandSpaceReusable}.
     */
    public IslandReservation reserveIslandLocation(final ParsedIslandStructure parsed) {
        final int height = Integer.parseInt(SkyblockAddonConfig.getForKey("island.spawn.height"));
        final IslandSlotAllocator.Allocation allocation = IslandManager.getInstance().allocateIslandSlot();
        final net.minecraft.core.Vec3i islandLocation = ForgeConverter.internalToForgeVec3i(allocation.center());
        final net.minecraft.core.Vec3i offset = islandLocation.offset(-(parsed.biggestX / 2), height, -(parsed.biggestZ / 2));

        final Set<ChunkPos> chunks = new HashSet<>();
        for (final BuildingBlock building : parsed.blocks) {
            if (building.getState().isAir()) continue;
            chunks.add(new ChunkPos(building.getPos().offset(offset)));
        }
        chunks.add(new ChunkPos(new BlockPos(islandLocation.getX(), height, islandLocation.getZ())));

        return new IslandReservation(islandLocation, height, offset, chunks, allocation.skippedSlots());
    }

    /**
     * Delete an island's file after any write of it that is still queued.
     */
    public void removeIslandNBT(final ForgeIsland data) {
        final UUID islandId = data.getId();
        final Path islandDataFolder = islandDataFolder();
        this.islandFileWriteTracker.forget(islandId);
        this.islandFileWriteQueue.queueDelete(islandId, failure -> LOGGER.error("Failed to delete the file of island {} in '{}'; delete it by hand with the server stopped or the island returns at the next start",
                islandId, islandDataFolder, failure));
    }

    public List<UUID> getPurgableIslands() {
        return IslandManager.getInstance().getEntrySet().entrySet().stream() // Create a stream of map entries
                .filter(entry -> ((ForgeIsland) entry.getValue()).purgeEligibility().purgeable())
                .map(Map.Entry::getKey) // Map to the UUID (key) of the entry
                .collect(Collectors.toList()); // Collect into a List<UUID>
    }
}
