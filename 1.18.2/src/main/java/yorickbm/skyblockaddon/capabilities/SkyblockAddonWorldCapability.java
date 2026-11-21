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
import yorickbm.skyblockaddon.core.util.IslandFileWriteTracker;
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
    private static final String RESERVED_SLOTS_TAG = "reservedSlots";
    MinecraftServer serverInstance;
    private Vec3i persistedNextFreshSlot = new Vec3i(0, 0, 0);
    private List<Vec3i> persistedReusableSlots = List.of();
    private List<IslandSlot> persistedAdminReservedSlots = List.of();
    private boolean islandsLoaded = false;
    private final IslandFileWriteTracker islandFileWriteTracker = new IslandFileWriteTracker();

    public SkyblockAddonWorldCapability(final MinecraftServer server) {
        serverInstance = server;

        //Init Island Manager
        IslandManager.getInstance().initializeCaches(server.getMaxPlayers());
    }

    /**
     * Save data into NBT.
     */
    public void saveNBTData(final CompoundTag nbt) {
        nbt.put("lastIsland", NBTUtil.Vec3iToNBT(IslandManager.getInstance().getNextFreshSlot()));
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

        final ListTag reservedSlotsTag = new ListTag();
        for (final IslandSlot reservedSlot : IslandManager.getInstance().getAdminReservedSlots()) {
            final CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("x", reservedSlot.x());
            slotTag.putInt("z", reservedSlot.z());
            reservedSlotsTag.add(slotTag);
        }
        nbt.put(RESERVED_SLOTS_TAG, reservedSlotsTag);
    }

    /**
     * Persist the in-memory islands that changed since their last write. Island files are written only here: the
     * overworld's {@code WorldEvent.Save} (autosave, save-all, stop) and the admin commands that must persist at once
     * call it; the capabilities.dat hook stores the grid state only. Serializes the live island objects, so it must run
     * on the server thread, where islands are changed.
     */
    public void saveIslandsToDisk() {
        if (!serverInstance.isSameThread()) {
            throw new IllegalStateException("Islands are saved from the live objects and must be saved on the server thread, not on " + Thread.currentThread().getName());
        }
        final long saveStartNanos = System.nanoTime();
        final NBTEncoder.IslandSaveReport report = NBTEncoder.saveChangedToFile(IslandManager.getInstance().getIslands().stream()
                .map(SkyblockAddonWorldCapability::asForgeIsland)
                .toList(), islandDataFolder(), this.islandFileWriteTracker);
        final long saveMillis = (System.nanoTime() - saveStartNanos) / 1_000_000L;
        if (report.written() > 0 || !report.failed().isEmpty()) {
            LOGGER.info("Saved islands in {} ms: {} written, {} unchanged, {} failed.", saveMillis, report.written(), report.skipped(), report.failed().size());
        } else {
            LOGGER.debug("Saved islands in {} ms: {} written, {} unchanged, {} failed.", saveMillis, report.written(), report.skipped(), report.failed().size());
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
        Vec3i lastLocation = NBTUtil.NBTToVec3i(nbt.getCompound("lastIsland"));
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
     * Parsed structure data ready for placement.
     * Pure data - safe to construct on any thread; consumed only on the server thread.
     */
    public static final class ParsedIslandStructure {
        public final List<BuildingBlock> blocks;
        public final int bigestX;
        public final int bigestZ;
        public final long parseMillis;

        public ParsedIslandStructure(final List<BuildingBlock> blocks, final int bigestX, final int bigestZ, final long parseMillis) {
            this.blocks = blocks;
            this.bigestX = bigestX;
            this.bigestZ = bigestZ;
            this.parseMillis = parseMillis;
        }
    }

    /**
     * Parse the island structure NBT into placement-ready data.
     * Thread-safe: only reads the structure NBT and builds POJOs - touches no world or singleton state.
     */
    public ParsedIslandStructure parseIslandStructure() {
        final long parseStartNanos = System.nanoTime();
        final CompoundTag nbt = Objects.requireNonNull(SkyBlockAddon.getIslandNBT(serverInstance), "island.nbt could not be read from the config folder or the mod jar");

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

        final long parseMillis = (System.nanoTime() - parseStartNanos) / 1_000_000L;
        return new ParsedIslandStructure(blocks, bigestX, bigestZ, parseMillis);
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
            return ForgeConverter.ForgeToInternalVec3i(islandLocation);
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
        final net.minecraft.core.Vec3i islandLocation = ForgeConverter.InternalToForgeVec3i(allocation.center());
        final net.minecraft.core.Vec3i offset = islandLocation.offset(-(parsed.bigestX / 2), height, -(parsed.bigestZ / 2));

        final Set<ChunkPos> chunks = new HashSet<>();
        for (final BuildingBlock building : parsed.blocks) {
            if (building.getState().isAir()) continue;
            chunks.add(new ChunkPos(building.getPos().offset(offset)));
        }
        chunks.add(new ChunkPos(new BlockPos(islandLocation.getX(), height, islandLocation.getZ())));

        return new IslandReservation(islandLocation, height, offset, chunks, allocation.skippedSlots());
    }

    public void removeIslandNBT(ForgeIsland data) {
        final Path worldPath = serverInstance.getWorldPath(LevelResource.ROOT).normalize();
        final Path filePath = worldPath.resolve("islanddata");

        NBTEncoder.removeFileFromFolder(filePath, data);
        this.islandFileWriteTracker.forget(data.getId());
    }

    public List<UUID> getPurgableIslands() {
        return IslandManager.getInstance().getEntrySet().entrySet().stream() // Create a stream of map entries
                .filter(entry -> entry.getValue().isAbandoned() && !((ForgeIsland)entry.getValue()).getModifiedChunks().isEmpty()) // Filter entries where island is abandoned
                .map(Map.Entry::getKey) // Map to the UUID (key) of the entry
                .collect(Collectors.toList()); // Collect into a List<UUID>
    }
}
