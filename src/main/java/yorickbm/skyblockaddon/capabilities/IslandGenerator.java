package yorickbm.skyblockaddon.capabilities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.Main;
import yorickbm.skyblockaddon.util.BuildingBlock;
import yorickbm.skyblockaddon.util.IslandData;
import yorickbm.skyblockaddon.util.NBTUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IslandGenerator {
    private static final Logger LOGGER = LogManager.getLogger();

    Vec3i lastLocation = IslandGeneratorProvider.DEFAULT_SPAWN;
    Vec3i spawnLocation = IslandGeneratorProvider.DEFAULT_SPAWN;

    HashMap<String, IslandData> islands = new HashMap<>();

    /**
     * Save islands data into nbt
     * @param nbt CompoundTag with all data
     */
    public void saveNBTData(CompoundTag nbt) {
        nbt.putInt("nbt-v", 2);
        nbt.put("lastIsland", NBTUtil.Vec3iToNBT(lastLocation));
        nbt.put("spawn", NBTUtil.Vec3iToNBT(spawnLocation));

        CompoundTag tagIslandIds = new CompoundTag();
        CompoundTag tagIslands = new CompoundTag();
        tagIslandIds.putInt("count", islands.size());
        int i = 0;

        for(Map.Entry<String, IslandData> island : islands.entrySet()) {
            tagIslandIds.putString(i+"", island.getKey());
            tagIslands.put(island.getKey(), island.getValue().serialize());
            i += 1;
        }

        nbt.put("islandIds", tagIslandIds);
        nbt.put("islands", tagIslands);
    }

    /**
     * Load islands from CompoundTag
     * @param nbt - CompoundTag containing data
     */
    public void loadNBTData(CompoundTag nbt) {
        if(nbt.contains("nbt-v")) {
            // Load NBT, based on stored NBT version type (Allows for dynamic further changes)
            switch (nbt.getInt("nbt-v")) {
                case 2 -> {
                    lastLocation = NBTUtil.NBTToVec3i(nbt.getCompound("lastIsland"));
                    spawnLocation = NBTUtil.NBTToVec3i(nbt.getCompound("spawn"));
                    CompoundTag tagIslandIds = nbt.getCompound("islandIds");
                    CompoundTag tagIslands = nbt.getCompound("islands");
                    for (int i = 0; i < tagIslandIds.getInt("count"); i++) {
                        String id = tagIslandIds.getString(i + "");
                        IslandData island = new IslandData(tagIslands.getCompound(id));
                        islands.put(id, island);
                    }
                }
            }
        } else {
            //Make sure old version NBT can still be loaded
            if(nbt.contains("loc-x"))
                lastLocation = new Vec3i(nbt.getInt("loc-x"), nbt.getInt("loc-y"), nbt.getInt("loc-z"));
            if(nbt.contains("spawn-x"))
                spawnLocation = new Vec3i(nbt.getInt("spawn-x"), nbt.getInt("spawn-y"), nbt.getInt("spawn-z"));
        }
    }

    public void destroyIsland(Level world, Vec3i center) {
        Thread asyncDestroy = new Thread(() -> BlockPos.betweenClosed(
            new BlockPos(
                center.getX()-IslandGeneratorProvider.SIZE,
                world.getMinBuildHeight(),
                center.getZ()-IslandGeneratorProvider.SIZE),
            new BlockPos(
                center.getX()+IslandGeneratorProvider.SIZE,
                world.getMaxBuildHeight(),
                center.getZ()+IslandGeneratorProvider.SIZE
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
     * Generate an island from NBT data file stored within resources of this plugin
     * Island is template given from Iskall85 Vaulthunters S3 modpack
     * @param worldServer World you wish island to be generated within
     * @return Spawn location of island
     * @throws IOException Exception to be thrown if resource not found
     */
    public Vec3i genIsland(ServerLevel worldServer) throws IOException {

        MinecraftServer server = worldServer.getServer();
        Resource rs = server.getResourceManager().getResource(new ResourceLocation(Main.MOD_ID, "structures/island.nbt"));
        CompoundTag nbt = NbtIo.readCompressed(rs.getInputStream());

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
            return new Vec3i(IslandGeneratorProvider.DEFAULT_SPAWN.getX(), IslandGeneratorProvider.DEFAULT_SPAWN.getY(), IslandGeneratorProvider.DEFAULT_SPAWN.getZ());
        }

        lastLocation = nextGridLocation(lastLocation);

        final int finalBigestX = bigestX, finalBigestZ = bigestZ;
        blocks.forEach(block -> block.place(worldServer, lastLocation.offset(-(finalBigestX /2),IslandGeneratorProvider.MIN_HEIGHT,-(finalBigestZ /2))));

        return new Vec3i(lastLocation.getX(), 121, lastLocation.getZ());
    }

    /**
     * Get next Vec3i for an island based on snake pattern island generation algorithm
     * @param location Last island location
     * @return Location for next island
     */
    private Vec3i nextGridLocation(final Vec3i location) {
        final int x = location.getX();
        final int z = location.getZ();
        final int d = IslandGeneratorProvider.SIZE *2 + IslandGeneratorProvider.BUFFER;

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
        for(Map.Entry<String, IslandData> island : islands.entrySet()) {
            IslandData data = island.getValue();

            if(data.isOwner(uuid) || data.hasMember(uuid))
                return island.getKey();
        }

        return "";
    }

    /**
     * Find island data by its location for legacy conversion
     * @param location Vec3i islands spawn location
     * @return Island ID or empty if not found
     */
    public String findIslandForLocation(Vec3i location) {
        for(Map.Entry<String, IslandData> island : islands.entrySet()) {
            IslandData data = island.getValue();

            if(data.getSpawn() == location)
                return island.getKey();
        }

        return "";
    }

    /**
     * Convert users legacy data into new Island Data System
     * @param i Legacy Player Island Data
     * @param player Players whom legacy data is given
     */
    public void registerIslandFromLegacy(PlayerIsland i, Player player) {
        String islandId = findIslandForLocation(i.getLocation());
        IslandData data;

        //Register island or collect island if already exists
        if(islandId.equals("")) {
            data = new IslandData(null, Vec3i.ZERO);
            islandId = registerIsland(data);
            LOGGER.info("New legacy island created from " + player.getGameProfile().getName() + " ("+islandId+")");
        }
        else {
            LOGGER.info("Added legacy user " + player.getGameProfile().getName() + " to island "+islandId+".");
            data = islands.get(islandId);
        }

        //Update island information
        if(i.isOwner()) data.setOwner(player.getUUID());
        else data.addIslandMember(player.getUUID());

        if(data.getSpawn() == Vec3i.ZERO) data.setSpawn(i.getLocation());

        //Update player information
        i.setIsland(islandId);
    }

    /**
     * Register an island into the world data
     * @param island Island data to register
     * @return ID of registration
     */
    public String registerIsland(IslandData island) {
        String islandId = UUID.randomUUID().toString();
        islands.put(islandId, island);
        return islandId;
    }

    /**
     * Get island data for island by its ID
     * @param islandId ID of Island
     * @return Island Data
     */
    public IslandData getIslandById(String islandId) {
        return islands.get(islandId);
    }
}
