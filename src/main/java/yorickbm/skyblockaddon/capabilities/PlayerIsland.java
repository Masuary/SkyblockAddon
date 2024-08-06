package yorickbm.skyblockaddon.capabilities;

import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

public class PlayerIsland {
    //Legacy data
    private Vec3i centerLocation = new Vec3i(0,0,0);
    private boolean isOwner = false;
    private boolean isLegacy = false;

    //Legacy functions
    public boolean isOwner() { return isOwner; }
    public Vec3i getLocation() {
        if(centerLocation != null && centerLocation.getX() == 0 && centerLocation.getY() == 0 && centerLocation.getZ() == 0) return null;
        return centerLocation;
    }

    //New data
    private UUID islandId = null;
    private UUID oldIslandId = null; //Allows to undo island leave through commando

    private final HashMap<UUID, Long> islandInvites = new HashMap<>();
    private final HashMap<UUID, Long> teleportInvites = new HashMap<>();
    private long creationTimestamp = Instant.now().getEpochSecond() - 220;

    /**
     * Check if invite is valid with criteria.
     * Within 60 minutes.
     * @param islandId Id of island to be invited to
     * @return Boolean
     */
    public boolean inviteValid(UUID islandId) {
        Object timestamp = islandInvites.get(islandId);
        islandInvites.remove(islandId); //One time trigger validation

        return timestamp != null && (long)timestamp >= (Instant.now().getEpochSecond() - (60 * 60)); //Check if invite is not older then x seconds
    }

    /**
     * Add island invite to player
     * @param islandId IslandId you are inviting player for
     */
    public void addInvite(UUID islandId) {
        islandInvites.put(islandId, Instant.now().getEpochSecond());
    }

    /**
     * Check if teleport is still valid for player
     * @param player Players whom request you wish to confirm
     * @return Boolean
     */
    public boolean teleportValid(UUID player) {
        Object timestamp = teleportInvites.get(player);
        teleportInvites.remove(player); //One time trigger validation

        return timestamp != null && (long)timestamp >= Instant.now().getEpochSecond() - 60; //Check if invite is not older then x seconds
    }

    /**
     * Determine if island island can be created or wait is required
     * @return wait time
     */
    public long CreateIslandDelay() {
        long delay = creationTimestamp - (Instant.now().getEpochSecond() - 120); //Check if last attempt is  older then x seconds
        if(delay <= 0) creationTimestamp = Instant.now().getEpochSecond();
        return delay;
    }

    /**
     * Add island teleport request to player for player
     * @param player UUID of whom want to teleport
     */
    public void addTeleport(UUID player) {
        teleportInvites.put(player, Instant.now().getEpochSecond());
    }

    /**
     * Check if player is currently part of an island
     * @return True or False
     */
    public boolean hasOne() { return islandId != null; }

    /**
     * Returns if the player data that has been loaded was legacy data or not
     * @return True or False
     */
    public boolean hasLegacyData() {
        return isLegacy;
    }

    /**
     * Save players island information into CompoundTag
     * @param nbt CompoundTag to save to
     * @return CompoundTag
     */
    public CompoundTag saveNBTData(CompoundTag nbt) {
        nbt.putInt("nbt-v", 3);
        nbt.putUUID("islandId", islandId);

        return nbt;
    }

    /**
     * Load players island information from CompoundTag
     * @param nbt CompoundTag containing information
     */
    public void loadNBTData(CompoundTag nbt) {
        if(nbt.contains("nbt-v")) {
            if (nbt.getInt("nbt-v") == 2) {
                islandId = UUID.fromString(nbt.getString("islandId"));
            } if (nbt.getInt("nbt-v") >= 3) {
                islandId = nbt.getUUID("islandId");
            } else {
                islandId = null;
            }
        } else {
            if(nbt.contains("loc-x")) centerLocation = new Vec3i(nbt.getInt("loc-x"),nbt.getInt("loc-y"),nbt.getInt("loc-z"));
            if(nbt.contains("isOwner")) isOwner = nbt.getBoolean("isOwner");
            isLegacy = true;
        }
    }

    /**
     * Set players island by ID
     * @param id ID of island to set
     */
    public void setIsland(UUID id) {
        oldIslandId = islandId;
        islandId = id;
    }

    /**
     * Get players previous island he/she/they was part of.
     * @return ID of island
     */
    public UUID getPreviousIsland() {
        return oldIslandId;
    }

    /**
     * Function to clone data over
     * @param oldStore Old island
     */
    public void copyFrom(PlayerIsland oldStore) {
        this.islandId = oldStore.islandId;
        this.oldIslandId = oldStore.oldIslandId;
    }

    /**
     * Get ID for island currently player part of is
     * @return Island ID
     */
    public UUID getIslandId() {
        return islandId;
    }
}
