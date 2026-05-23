package yorickbm.skyblockaddon.capabilities;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
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

    public net.minecraft.core.Vec3i genIsland(ServerLevel level) {
        final net.minecraft.core.Vec3i islandLocation = ForgeConverter.InternalToForgeVec3i(IslandManager.getInstance().getNextIslandGen());
        final CompoundTag nbt = SkyBlockAddon.getIslandNBT(level.getServer());

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

        final int finalBigestX = bigestX;
        final int finalBigestZ = bigestZ;
        final int height = Integer.parseInt(SkyblockAddonConfig.getForKey("island.spawn.height"));
        blocks.stream().filter(block -> !block.getState().isAir()).forEach(block -> block.place(level, islandLocation.offset(-(finalBigestX / 2), height, -(finalBigestZ / 2))));

        final ChunkAccess chunk = level.getChunk(new BlockPos(islandLocation.getX(), height, islandLocation.getZ()));
        final int topHeight = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, islandLocation.getX(), islandLocation.getZ()) + 2;

        final net.minecraft.core.Vec3i rslt = new net.minecraft.core.Vec3i(islandLocation.getX(), topHeight, islandLocation.getZ());
        return rslt;
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
