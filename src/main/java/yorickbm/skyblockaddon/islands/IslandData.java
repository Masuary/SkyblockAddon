package yorickbm.skyblockaddon.islands;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.util.INBTSerializable;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import yorickbm.skyblockaddon.SkyblockAddon;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.islands.permissions.Permission;
import yorickbm.skyblockaddon.util.ServerHelper;
import yorickbm.skyblockaddon.util.Square;
import yorickbm.skyblockaddon.util.UsernameCache;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IslandData implements INBTSerializable<CompoundTag> {

    private static final Logger LOGGER = LogManager.getLogger();

    private UUID id = null; //UUID Of island
    private UUID owner = null; //UUID Of owner
    private Vec3i spawn; //Spawn coordinates of island
    private Vec3i center; //Spawn coordinates of island
    private String biome = "Unknown";
    private boolean travelability = false;
    private Square boundingBoxHelper = null;

    private PermissionGroup Admin, Members, Default, Owner;
    private final List<PermissionGroup> permissionGroups = new ArrayList<>();

    public IslandData() {  }

    /**
     * Allow for generation from legacy data
     */
    public IslandData(UUID playerUUID, Vec3i location) {
        owner = playerUUID;
        center = location;
        spawn = location;

        this.Admin = new PermissionGroup("Admin", Items.RED_MUSHROOM_BLOCK, true);
        this.Members = new PermissionGroup("Members", Items.BROWN_MUSHROOM_BLOCK, true);
        this.Default = new PermissionGroup("Default", Items.MUSHROOM_STEM,false);
        this.Owner = new PermissionGroup("Admin", Items.AIR, true); //Allow everything, no visible display item

        permissionGroups.add(this.Admin);
        permissionGroups.add(this.Members);
        permissionGroups.add(this.Default);
        permissionGroups.add(new PermissionGroup("Friends", Items.PAPER, false));
        permissionGroups.add(new PermissionGroup("Coop", Items.PAPER,false));
        permissionGroups.add(new PermissionGroup("Miscellaneous", Items.MUSIC_DISC_13,false));
    }

    /**
     * Remove player from members list, and make it the owner of the island
     * @param uuid Player you wish to make owner of island
     */
    public void setOwner(UUID uuid) {
        this.Members.removeMember(uuid);
        this.Admin.removeMember(uuid);
        if(owner != null && owner != uuid) this.Admin.addMember(owner); //Demote to admin

        owner = uuid;
    }

    /**
     * Remove owner of island, and promote a new member or admin as owner
     * @param uuid UUID of player whom should be removed as owner
     * @return If owner has been removed
     */
    public boolean removeOwner(UUID uuid) {
        if(!isOwner(uuid)) return false;

        if(!this.Admin.getMembers().isEmpty())
            setOwner(this.Admin.getMembers().get(0)); //Set new owner if necessary
        else if(!this.Members.getMembers().isEmpty())
            setOwner(this.Members.getMembers().get(0)); //Set new owner if necessary
        else {
            return false;
        }

        this.makeAdmin(uuid);
        return true;
    }

    /**
     * Change player permission group membership from member to admin
     * @param uuid - Player to switch
     */
    public void makeAdmin(UUID uuid) {
        this.Admin.addMember(uuid);
        this.Members.removeMember(uuid);
    }

    /**
     * Change player permission group membership from admin to member
     * @param uuid - Player to switch
     */
    public void removeAdmin(UUID uuid) {
        this.Members.addMember(uuid);
        this.Admin.removeMember(uuid);
    }

    /**
     * Add member to island by UUID
     * @param uuid UUID of p[layer you wish to add
     */
    public void addIslandMember(UUID uuid) {
        this.Members.addMember(uuid);

        if(!hasOwner()) setOwner(uuid); //Set owner
        if(isOwner(uuid)) this.Members.removeMember(uuid);
    }

    /**
     * Remove member from island by uuid
     * @param uuid UUID of player you wish to remove
     */
    public void removeIslandMember(UUID uuid) {
        if(isOwner(uuid) && !removeOwner(uuid))
            setOwner(null);

        if(this.Members.getMembers().contains(uuid))
            this.Members.removeMember(uuid);
        if(this.Admin.getMembers().contains(uuid))
            this.Admin.removeMember(uuid);
    }

    /**
     * Change spawn location for island
     * @param location New location you wish islands spawn to be at
     */
    public void setSpawn(Vec3i location) {
        spawn = location;
    }

    /**
     * Get Vec3i for island spawn location
     * @return Vec3i
     */
    public Vec3i getSpawn() {
        if(spawn == null) return Vec3i.ZERO;
        return spawn;
    }

    /**
     * Get Vec3i for center location of island
     * @return Vec3i
     */
    public Vec3i getCenter() {
        if(center == null) return Vec3i.ZERO;
        return center;
    }

    /**
     * Check if certain player is owner of island
     * @param uuid UUID of player you wish to check
     * @return True or false based on fact if player is owner
     */
    public boolean isOwner(UUID uuid) {
        if (owner == null) return false;
        return owner.toString().equals(uuid.toString());
    }

    /**
     * Check if UUID is part of island members and by indication not owner
     * @param uuid UUID of player you wish to check
     * @return True or False based on fact if player is member of island
     */
    public boolean hasMember(UUID uuid) {
        return this.Admin.getMembers().contains(uuid) || this.Members.getMembers().contains(uuid);
    }

    public boolean partOfAnyGroup(UUID uuid) {
        AtomicBoolean isOf = new AtomicBoolean(false);

        this.permissionGroups.forEach(pg -> {
            if(pg.hasMember(uuid)) isOf.set(true);
        });

        return isOf.get();
    }

    /**
     * Get all members that are part of Island
     * @return List of UUID's
     */
    public List<UUID> getMembers() {
        return Stream.concat(this.Admin.getMembers().stream(), this.Members.getMembers().stream()).collect(Collectors.toList());
    }

    /**
     * Teleport player to islands spawn coordinates
     * @param entity whom you want to teleport
     */
    public void teleport(Entity entity) {
        entity.teleportTo(spawn.getX(), spawn.getY(), spawn.getZ());

        if(entity instanceof ServerPlayer player) {
            ServerHelper.playSongToPlayer(player, SoundEvents.ENDERMAN_TELEPORT, SkyblockAddon.EFFECT_SOUND_VOL, 1f);
        }
    }

    /**
     * Get Game Profile for owner of island
     * @return GameProfile
     */
    public GameProfile getOwner(MinecraftServer server) {
        try {
            if(hasOwner()) {
                Player player = server.getPlayerList().getPlayer(owner);
                if(player != null) return player.getGameProfile();
                else return new GameProfile(owner, UsernameCache.getBlocking(owner));
            }
        } catch (IOException ignored) {} //API will fail on offline servers
        return new GameProfile(UUID.randomUUID(), "Unknown");
    }

    /**
     * Check if island has owner
     * @return boolean
     */
    public boolean hasOwner() {
        return owner != null;
    }

    /**
     * Get biome name of island
     * @return Printable name of biome
     */
    public String getBiome() {
        return this.biome;
    }

    /**
     * Set islands biome
     * @param biome - Biome you wish to modify to
     */
    public void setBiome(ServerLevel serverlevel, Holder<Biome> biome, String name) {
        this.biome = name; //Set name for GUI
        BoundingBox boundingbox = this.getIslandBoundingBox();

        MutableInt mutableint = new MutableInt(0);

        //Get all chunks within bounding box
        for(int j = SectionPos.blockToSectionCoord(boundingbox.minZ()); j <= SectionPos.blockToSectionCoord(boundingbox.maxZ()); ++j) {
            for(int k = SectionPos.blockToSectionCoord(boundingbox.minX()); k <= SectionPos.blockToSectionCoord(boundingbox.maxX()); ++k) {
                ChunkAccess chunkaccess = serverlevel.getChunk(k, j, ChunkStatus.FULL, false);
                if (chunkaccess == null) {
                    continue; //Skip unloaded chunks
                }
                chunkaccess.fillBiomesFromNoise(
                        makeResolver(mutableint, chunkaccess, boundingbox, biome, (p_262543_) -> true),
                        serverlevel.getChunkSource().getGenerator().climateSampler());
                updateChunk(serverlevel.getChunk(k, j), serverlevel);
            }
        }
    }

    /**
     * Send chunk update to player client
     * @param levelChunk Chunk to update
     * @param serverlevel Server level chunk is part off
     */
    private void updateChunk(@NotNull LevelChunk levelChunk, ServerLevel serverlevel) {
        ChunkPos chunkPos = levelChunk.getPos();
        MutableObject<ClientboundLevelChunkWithLightPacket> mutableObject = new MutableObject<>();

        for(ServerPlayer serverPlayer : serverlevel.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            if(mutableObject.getValue() == null) {
                mutableObject.setValue(new ClientboundLevelChunkWithLightPacket(levelChunk, serverlevel.getLightEngine(), null, null, true));
            }
            //serverPlayer.untrackChunk(chunkPos);
            serverPlayer.trackChunk(chunkPos, mutableObject.getValue());
        }
    }

    /**
     * Get bounding box for island from center point
     * @return BoundingBox of Island
     */
    public BoundingBox getIslandBoundingBox() {
        int size = Integer.parseInt(SkyblockAddonConfig.getForKey("island.size.radius"));
        BlockPos blockpos = quantize(new BlockPos(center.getX() - size,-100,center.getZ() - size));
        BlockPos blockpos1 = quantize(new BlockPos(center.getX() + size,350,center.getZ() + size));
        return BoundingBox.fromCorners(blockpos, blockpos1);
    }

    /**
     * Get permission from group player is part of, if no group then default permission group will be used.
     * @param permission Permission you wish to retrieve
     * @param player Players UUID whose group you wish to use
     * @return Permission for user
     */
    public Permission getPermission(Permissions permission, UUID player) {
        return getGroupForPlayer(player).getPermission(permission);
    }

    /**
     * Get permission group player is listed for.
     * @param player Whom you want to retrieve info from
     * @return Permission group player is part of
     */
    public PermissionGroup getGroupForPlayer(UUID player) {
        if(isOwner(player)) return this.Owner;

        Optional<PermissionGroup> group = permissionGroups.stream().filter(g -> g.hasMember(player)).findFirst();
        if(group.isEmpty()) group = Optional.of(this.Default);

        return group.get();
    }

    /**
     * Get all permission groups made for island
     * @return List of groups
     */
    public List<PermissionGroup> getPermissionGroups() {
        return this.permissionGroups;
    }

    /**
     * Check if player is part of the Admin Group of an island
     * @param player UUID of player you wish to check if he/she is an admin.
     * @return Boolean
     */
    public boolean isIslandAdmin(UUID player) {
        if(isOwner(player)) return true;
        return this.Admin.hasMember(player);
    }

    /**
     * Check if the player is a member of the island, either member, admin, or owner.
     * @param player UUID of player you wish to check if he/she is an admin.
     * @return Boolean
     */
    public boolean isMember(UUID player) {
        return isIslandAdmin(player) || isOwner(player) || isIslandMember(player);
    }

    public boolean isIslandMember(UUID uuid) {
        return this.Members.hasMember(uuid);
    }

    /**
     * Biome Modification Utilities
     */
    private static int quantize(int p_261998_) {
        return QuartPos.toBlock(QuartPos.fromBlock(p_261998_));
    }
    private static BlockPos quantize(BlockPos p_262148_) {
        return new BlockPos(quantize(p_262148_.getX()), quantize(p_262148_.getY()), quantize(p_262148_.getZ()));
    }
    private static BiomeResolver makeResolver(MutableInt p_262615_, ChunkAccess p_262698_, BoundingBox p_262622_, Holder<Biome> p_262705_, Predicate<Holder<Biome>> p_262695_) {
        return (p_262550_, p_262551_, p_262552_, p_262553_) -> {
            int i = QuartPos.toBlock(p_262550_);
            int j = QuartPos.toBlock(p_262551_);
            int k = QuartPos.toBlock(p_262552_);
            Holder<Biome> holder = p_262698_.getNoiseBiome(p_262550_, p_262551_, p_262552_);
            if (p_262622_.isInside(new Vec3i(i, j, k)) && p_262695_.test(holder)) {
                p_262615_.increment();
                return p_262705_;
            } else {
                return holder;
            }
        };
    }

    @Override
    public String toString() {
        return "\"center\"=\"%s\",\"hasOwner\"=\"%s\"".formatted(this.getCenter(), this.hasOwner());
    }

    /**
     * Is the island public
     * @return boolean
     */
    public boolean getTravelability() {
        return travelability;
    }

    /**
     * Set the island public or private
     * @param bool - Should island be public?
     */
    public void setTravelability(boolean bool) {
        travelability = bool;
    }

    /**
     * Get island bounding box.
     * If a bounding box isn't yet generated create one
     *
     * @return Square
     */
    public Square getBoundingBoxHelper() {
        if(boundingBoxHelper == null) {
            BoundingBox box = getIslandBoundingBox();
            boundingBoxHelper = new Square(new Vec3i(box.minX(), 0, box.minZ()), new Vec3i(box.maxX(), 0, box.maxZ()));
        }

        return boundingBoxHelper;
    }

    /**
     * Convert location to a location on the edges of the square by usage of distance determinations.
     * @param location Location to convert into an edge location
     *
     * @return Vec3i
     */
    public Vec3i getLocationOnEdge(Vec3i location) {
        Square box = getBoundingBoxHelper();

        int distance1 = ServerHelper.calculateDistance(location, box.corner1());
        int distance2 = ServerHelper.calculateDistance(location, box.corner2());

        Vec3i closestCorner = (distance1 < distance2) ? box.corner1() : box.corner2();
        int xDiff = Math.abs(closestCorner.getX() - location.getX());
        int zDiff = Math.abs(closestCorner.getZ() - location.getZ());

        if(xDiff < zDiff) {
            return new Vec3i(closestCorner.getX(), location.getY(), location.getZ());
        } else {
            return new Vec3i(location.getX(), location.getY(), closestCorner.getZ());
        }
    }

    /**
     * Turn island data into a NBT Tag contain its data that can be used to create a new instance.
     * @return NBT Tag that can be stored
     */
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if(owner != null) tag.putString("owner", owner.toString());
        if(id != null) tag.putUUID("Id", id);
        if(spawn == null) spawn = Vec3i.ZERO;

        CompoundTag slocation = new CompoundTag();
        slocation.putInt("x", spawn.getX());
        slocation.putInt("y", spawn.getY());
        slocation.putInt("z", spawn.getZ());
        tag.put("spawn", slocation);

        CompoundTag clocation = new CompoundTag();
        clocation.putInt("x", center.getX());
        clocation.putInt("y", center.getY());
        clocation.putInt("z", center.getZ());
        tag.put("center", clocation);

        tag.putString("biome", biome);
        tag.putBoolean("travelability", travelability);

        CompoundTag permissionData = new CompoundTag();
        CompoundTag groups = new CompoundTag();
        groups.putInt("count", permissionGroups.size());
        for(int i = 0; i < permissionGroups.size(); i++) {
            groups.putString("group-" + i, permissionGroups.get(i).getName());
            permissionData.put(permissionGroups.get(i).getName(), permissionGroups.get(i).serialize());
        }

        permissionData.put("groups", groups);
        tag.put("permissions", permissionData);

        return tag;
    }

    /**
     * Load island data from CompoundTag
     * @param tag CompoundTag containing island data
     */
    @Override
    public void deserializeNBT(CompoundTag tag) {
        try {
            if (tag.contains("owner")) owner = UUID.fromString(tag.getString("owner"));
            if (tag.contains("Id")) id = tag.getUUID("Id");

            CompoundTag slocation = (CompoundTag) tag.get("spawn");
            spawn = new Vec3i(slocation.getInt("x"), slocation.getInt("y"), slocation.getInt("z"));
            CompoundTag clocation = (CompoundTag) tag.get("center");
            center = new Vec3i(clocation.getInt("x"), clocation.getInt("y"), clocation.getInt("z"));

            if (center.distToCenterSqr(0, 0, 0) <= 4) center = spawn; //Island center was set at 0, 0, 0 not possible!

            biome = tag.getString("biome");
            travelability = tag.getBoolean("travelability");

            CompoundTag permissionData = (CompoundTag) tag.get("permissions");
            CompoundTag groups = (CompoundTag) permissionData.get("groups");
            int gCount = groups.getInt("count");
            for (int i = 0; i < gCount; i++) {
                PermissionGroup group = new PermissionGroup(permissionData.getCompound(groups.getString("group-" + i)));
                if (!group.canBeRemoved()) {
                    switch (group.getName()) {
                        case "Admin" -> this.Admin = group;
                        case "Members" -> this.Members = group;
                        case "Default" -> this.Default = group;
                    }
                }

                permissionGroups.add(group);
            }
            this.Owner = new PermissionGroup("Admin", Items.AIR, true); //Allow everything, no visible display item

            //Load legacy member data into permission member group
            if (tag.contains("members")) {
                CompoundTag members = tag.getCompound("members");
                for (int x = 0; x < members.getInt("count"); x++) {
                    UUID member = UUID.fromString(members.getString("member-" + x));
                    this.Members.addMember(member);
                }
            }
        } catch(Exception ex) {
            LOGGER.error(ex);
            LOGGER.error("Failed to load island with id: {}", tag.getUUID("Id"));
        }
    }

    /**
     * Get the islands UUID
     * @return UUID
     */
    public UUID getId() {
        return id;
    }

    public void setId(UUID islandId) {
        id = islandId;
    }
}
