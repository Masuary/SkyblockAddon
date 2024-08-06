package yorickbm.skyblockaddon.capabilities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.SkyblockAddon;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.islands.PermissionGroup;
import yorickbm.skyblockaddon.util.BuildingBlock;
import yorickbm.skyblockaddon.util.NBTUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class IslandGenerator {
    private static final Logger LOGGER = LogManager.getLogger();

    Vec3i lastLocation = IslandGeneratorProvider.SPAWN;
    Vec3i spawnLocation = IslandGeneratorProvider.SPAWN;

    final HashMap<UUID, IslandData> islands = new HashMap<>();
    final HashMap<Vec3i, UUID> islandIdsByVec3i = new HashMap<>();



    /**
     * Save islands data into nbt
     * @param nbt CompoundTag with all data
     */
    public void saveNBTData(CompoundTag nbt, MinecraftServer server) {
        nbt.putInt("nbt-v", 4);
        nbt.put("lastIsland", NBTUtil.Vec3iToNBT(lastLocation));
        nbt.put("spawn", NBTUtil.Vec3iToNBT(spawnLocation));

        saveIslandsToFile(server);
    }

    /**
     * Load islands from CompoundTag
     * @param nbt - CompoundTag containing data
     */
    public void loadNBTData(CompoundTag nbt, MinecraftServer server) {
        if(nbt.contains("lastIsland")) lastLocation = NBTUtil.NBTToVec3i(nbt.getCompound("lastIsland"));
        if(nbt.contains("spawn")) spawnLocation = NBTUtil.NBTToVec3i(nbt.getCompound("spawn"));

        //Setup paths
        Path worldPath = server.getWorldPath(LevelResource.ROOT).normalize();
        Path islanddataPath = worldPath.resolve("islanddata");
        File islanddataDir = islanddataPath.toFile();

        //Create folder if needed
        if(!islanddataDir.exists()) {
            boolean reslt = islanddataDir.mkdirs();
            if(!reslt) {
                LOGGER.error("Failed to create islanddata location: " + islanddataDir.getAbsolutePath());
            }
        }

        if(nbt.getInt("nbt-v") <= 3) {
            lastLocation = nextGridLocation(lastLocation); //Get next location to work within the new system
            loadLegacy(nbt);
            return;
        }

        //List files into list
        List<Path> islandFiles = new ArrayList<>();
        try {
            Files.list(islanddataDir.toPath()).forEach(islandFiles::add);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(islandFiles.size() == 0) {
            LOGGER.warn("[skyblockaddon] No islands available to be loaded.");
            return;
        }

        LOGGER.info("[skyblockaddon] Loading islands from data... (" + islandFiles.size() + ")");
        for(Path path : islandFiles) {
            try (FileInputStream fileInputStream = new FileInputStream(path.toFile())) {
                CompoundTag islandNBT = NbtIo.readCompressed(fileInputStream);

                IslandData island = new IslandData();
                island.deserializeNBT(islandNBT);
                islands.put(islandNBT.getUUID("Id"), island);
                islandIdsByVec3i.put(island.getCenter(), islandNBT.getUUID("Id"));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("[skyblockaddon] Finished loading!");
    }

    public void saveIslandsToFile(MinecraftServer server) {
        for(IslandData data : islands.values()) {
            saveIslandToFile(data, server);
        }
    }

    public void saveIslandToFile(IslandData data, MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).normalize();
        Path islanddataPath = worldPath.resolve("islanddata");

        try {
            Path path = islanddataPath.resolve(data.getId().toString() + ".nbt");
            if(!islanddataPath.toFile().exists()) {
                boolean rslt = islanddataPath.toFile().mkdirs();
                if(!rslt) {
                    LOGGER.error("Failed to create islanddata location: " + islanddataPath.toFile().getAbsolutePath());
                    return;
                }
            }
            if(!Files.exists(path)) {
                Files.createFile(path);
            }
            CompoundTag nbt = data.serializeNBT();
            try (FileOutputStream fileOutputStream = new FileOutputStream(path.toFile())) {
                NbtIo.writeCompressed(nbt, fileOutputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Destroy an island, by deleting all blocks
     * @param world
     * @param center
     */
    public void destroyIsland(Level world, Vec3i center) {
        int size = Integer.parseInt(SkyblockAddonConfig.getForKey("island.size.radius"));
        Thread asyncDestroy = new Thread(() -> BlockPos.betweenClosed(
            new BlockPos(
                center.getX()-size,
                world.getMinBuildHeight(),
                center.getZ()-size),
            new BlockPos(
                center.getX()+size,
                world.getMaxBuildHeight(),
                center.getZ()+size
            )
        ).forEach(bp -> world.removeBlock(bp, true)));
        asyncDestroy.start();
    }

    /**
     * Get spawn location that is set by NBT
     * @return Vec3i
     */
    public Vec3i getSpawnLocation() {
        return spawnLocation;
    }

    /**
     * Set spawn location to a new spot.
     * Failes if islands have already been generated.
     * @param pos New spawn location
     * @return boolean
     */
    public boolean setSpawnLocation(Vec3i pos) {
        if(!islands.isEmpty()) return false; //Cannot modify when islands have been generated already

        spawnLocation = pos;
        return true;
    }

    /**
     * Generate an island from NBT data file stored within resources of this plugin
     * Island is template given from Iskall85 Vaulthunters S3 modpack
     * @param worldServer World you wish island to be generated within
     * @return Spawn location of island
     */
    public Vec3i genIsland(ServerLevel worldServer) {
        CompoundTag nbt = SkyblockAddon.getIslandNBT(worldServer.getServer());

        ListTag paletteNbt = nbt.getList("palette", 10);
        ListTag blocksNbt = nbt.getList("blocks", 10);

        ArrayList<BuildingBlock> blocks = new ArrayList<>();
        ArrayList<BlockState> palette = new ArrayList<>();
        int bigestX = 0, bigestZ = 0;

        for(int i = 0; i < paletteNbt.size(); i++) palette.add(NbtUtils.readBlockState(paletteNbt.getCompound(i)));
        for(int i = 0; i < blocksNbt.size(); i++) {
            CompoundTag blockNbt = blocksNbt.getCompound(i);
            ListTag blockPosNbt = blockNbt.getList("pos", 3);

            if(blockPosNbt.getInt(0) > bigestX) bigestX = blockPosNbt.getInt(0);
            if(blockPosNbt.getInt(2) > bigestZ) bigestZ = blockPosNbt.getInt(2);

            blocks.add(new BuildingBlock(
                    new BlockPos(
                            blockPosNbt.getInt(0),
                            blockPosNbt.getInt(1),
                            blockPosNbt.getInt(2)
                    ),
                    palette.get(blockNbt.getInt("state"))
            ));
        }

        if(blocks.isEmpty()) {
            return null;
        }

        Vec3i islandLocation = lastLocation;
        lastLocation = nextGridLocation(lastLocation); //Store next location.

        final int finalBigestX = bigestX, finalBigestZ = bigestZ;
        final int height = Integer.parseInt(SkyblockAddonConfig.getForKey("island.spawn.height"));
        blocks.forEach(block -> block.place(worldServer, islandLocation.offset(-(finalBigestX /2), height ,-(finalBigestZ /2))));

        ChunkAccess chunk = worldServer.getChunk(new BlockPos(islandLocation.getX(), height ,islandLocation.getZ()));
        int topHeight = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, islandLocation.getX(), islandLocation.getZ()) +2;

        return new Vec3i(islandLocation.getX(), topHeight, islandLocation.getZ());
    }

    /**
     * Get next Vec3i for an island based on snake pattern island generation algorithm
     * @param location Last island location
     * @return Location for next island
     */
    private Vec3i nextGridLocation(final Vec3i location) {
        final int x = location.getX();
        final int z = location.getZ();
        final int d = Integer.parseInt(SkyblockAddonConfig.getForKey("island.size.radius")) * 2 + IslandGeneratorProvider.BUFFER;

        if(x < z) {
            if(-1 * x < z) return new Vec3i(x + d, 0, z);
            return new Vec3i(x, 0, z + d);
        }

        if(x > z) {
            if(-1 * x >= z)  return new Vec3i(x - d, 0, z);
            return new Vec3i(x, 0, z - d);
        }

        if( x <= 0) return new Vec3i(x, 0, z + d);
        return new Vec3i(x, 0, z - d);
    }

    /**
     * Go through all islands registered, find an island that UUID is part of
     * @param uuid UUID you wish to get Island ID for
     * @return Island ID or empty string if none found
     */
    public String findIslandForUUID(UUID uuid) {
        for(Map.Entry<UUID, IslandData> island : islands.entrySet()) {
            IslandData data = island.getValue();

            if(data.isOwner(uuid) || data.hasMember(uuid))
                return island.getKey().toString();
        }

        return "";
    }

    /**
     * Find island data by its location (for legacy conversion)
     * @param location Vec3i islands spawn location
     * @return Island ID or empty if not found
     */
    public UUID findIslandForLocation(Vec3i location) {
        for(Map.Entry<UUID, IslandData> island : islands.entrySet()) {
            IslandData data = island.getValue();

            if(data.getSpawn() == location)
                return island.getKey();
        }

        return null;
    }

    /**
     * Get island based on player location rounded and offset by spawn
     * If failed it loops through all islands to find island where your inside bounding box
     * @param location Player Location
     * @return IslandId or empty string
     */
    public UUID getIslandIdByLocation(Vec3i location) {
        final int d = Integer.parseInt(SkyblockAddonConfig.getForKey("island.size.radius")) * 2 + IslandGeneratorProvider.BUFFER;

        long offsetX = Math.round(location.getX()/(d*1.0)) + getSpawnLocation().getX();
        long offsetZ = Math.round(location.getZ()/(d*1.0)) + getSpawnLocation().getZ();

        Vec3i calculatedCenter = new Vec3i(offsetX, 121, offsetZ);
        UUID islandId = this.islandIdsByVec3i.get(calculatedCenter);
        if(islandId != null) return islandId;

        for(Map.Entry<UUID, IslandData> island : islands.entrySet()) {
            IslandData data = island.getValue();
            if(data.getIslandBoundingBox().isInside(location)) return island.getKey();
        }

        return null;
    }

    /**
     * Register an island into the world data
     * @param island Island data to register
     * @return ID of registration
     */
    public UUID registerIsland(IslandData island) {
        UUID islandId = UUID.randomUUID();
        island.setId(islandId);
        islands.put(islandId, island);
        return islandId;
    }

    /**
     * Get island data for island by its ID
     * @param islandId ID of Island
     * @return Island Data
     */
    public IslandData getIslandById(UUID islandId) {
        return islands.get(islandId);
    }

    public List<IslandData> getPublicTeleportIslands() {
        return  islands.values().stream()
                .filter(IslandData::hasOwner)
                .filter(IslandData::getTravelability)
                .toList();
    }

    /**
     * Load NBT lvl 3 Legacy data
     * @param nbt
     */
    public void loadLegacy(CompoundTag nbt) {
        // Alter NBT data if needed
        CompoundTag tagIslandIds = nbt.getCompound("islandIds");
        CompoundTag tagIslands = nbt.getCompound("islands");

        LOGGER.info("[skyblockaddon] Loading islands from legacy data: " + tagIslandIds.getInt("count"));
        for (int i = 0; i < tagIslandIds.getInt("count"); i++) {
            String id = tagIslandIds.getString(String.valueOf(i));
            CompoundTag islandTag = tagIslands.getCompound(id);

            //Generate default NBT values
            if(!islandTag.contains("biome")) islandTag.putString("biome", "UNKNOWN");
            if(!islandTag.contains("travelability")) islandTag.putBoolean("travelability", false);
            if(!islandTag.contains("center")) islandTag.put("center", islandTag.getCompound("spawn"));

            //Generate default permission groups
            if(!islandTag.contains("permissions") || !islandTag.getCompound("permissions").contains("groups")) {
                CompoundTag groups = new CompoundTag();
                groups.putInt("count", 6);
                groups.putString("group-" + 0, "Admin");
                groups.putString("group-" + 1, "Members");
                groups.putString("group-" + 2, "Default");
                groups.putString("group-" + 3, "Friends");
                groups.putString("group-" + 4, "Coop");
                groups.putString("group-" + 5, "Miscellaneous");

                CompoundTag permissionData = new CompoundTag();
                permissionData.put("groups", groups);
                permissionData.put("Admin", new PermissionGroup("Admin", Items.RED_MUSHROOM_BLOCK, true).serialize());
                permissionData.put("Members", new PermissionGroup("Members", Items.BROWN_MUSHROOM_BLOCK, true).serialize());
                permissionData.put("Default", new PermissionGroup("Default", Items.MUSHROOM_STEM,false).serialize());
                permissionData.put("Friends", new PermissionGroup("Friends", Items.PAPER, false).serialize());
                permissionData.put("Coop", new PermissionGroup("Coop", Items.PAPER,false).serialize());
                permissionData.put("Miscellaneous", new PermissionGroup("Miscellaneous", Items.MUSIC_DISC_13,false).serialize());
                islandTag.put("permissions", permissionData);
            }

            IslandData island = new IslandData();
            islandTag.putUUID("Id", UUID.fromString(id));
            island.deserializeNBT(islandTag);
            islands.put(UUID.fromString(id), island);
            islandIdsByVec3i.put(island.getCenter(), UUID.fromString(id));
        }
    }

    public Collection<IslandData> getIslands() {
        return islands.values();
    }
}
