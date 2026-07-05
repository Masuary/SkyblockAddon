package yorickbm.skyblockaddon.core.islands;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.core.util.geometry.BoundingBox;
import yorickbm.skyblockaddon.core.util.geometry.ChunkRef;
import yorickbm.skyblockaddon.core.util.geometry.Square;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Island {
    private UUID id = UUID.randomUUID(); //UUID Of island
    protected UUID owner = SkyblockAddonCore.MOD_UUID; //UUID Of owner

    protected Vec3i spawn; //Spawn coordinates of island
    protected Vec3i center; //Spawn coordinates of island

    protected volatile String name = "";
    private final AtomicBoolean nameFetching = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Runnable> nameCallbacks = new CopyOnWriteArrayList<>();
    protected String biome = "Unknown";
    protected boolean travelability = false;

    protected String skullTexture = "";

    protected final List<UUID> members = new ArrayList<>();
    protected final Map<UUID, IslandGroup> islandGroups = new HashMap<>();
    private final List<ChunkRef> loadedChunks = new ArrayList<>();

    /**
     * Empty constructor
     */
    public Island() {}

    /**
     * Set island's custom skull texture
     * If empty or below 10 characters custom texture will be removed
     * @param base64 - Texture string as base64
     */
    public void setSkullTexture(String base64) {
        if(base64.length() < 10) { this.skullTexture = ""; return; }
        this.skullTexture = base64;
    }
    public String getSkullTexture() {
        return this.skullTexture;
    }

    /**
     * Retrieve list of island groups
     */
    public Collection<IslandGroup> getGroups() { return Collections.unmodifiableCollection(islandGroups.values()); }

    /**
     * Determine if a certain group is part of the island
     * @param uuid - Group UUID
     * @return - If the group exists for the island
     */
    public boolean hasGroup(final UUID uuid) {
        return islandGroups.containsKey(uuid);
    }

    /**
     * Retrieve island group object by UUID
     * @param uuid - Group UUID
     * @return - IslandGroup Object
     */
    public IslandGroup getGroup(final UUID uuid) {
        return islandGroups.get(uuid);
    }

    /**
     * Register group object for island
     * @param group - Group Object
     */
    public void addGroup(final IslandGroup group) {
        islandGroups.put(group.getId(), group);
    }

    /**
     * Remove group safely from island, automatically moves all groups members to islands default group
     * @param uuid - Group UUID
     * @return - Group has been removed successfully
     *         - Fails if it tries to remove default group, or non existed one
     */
    public boolean removeGroup(final UUID uuid) {
        if(islandGroups.size() <= 1 || uuid.equals(SkyblockAddonCore.MOD_UUID) || uuid.equals(SkyblockAddonCore.MOD_UUID2)) return false;
        if(!islandGroups.containsKey(uuid)) return false;

        final IslandGroup groupToRemove = islandGroups.get(uuid);
        if(!groupToRemove.getMembers().isEmpty()) {
            new ArrayList<>(groupToRemove.getMembers()).forEach(entity -> {
                if(this.members.contains(entity)) {
                    this.assignGroup(entity, SkyblockAddonCore.MOD_UUID);
                }
            });
        }

        islandGroups.remove(uuid);
        return true;
    }

    /**
     * Determine if entity is part of any group within the island
     * @param entity - Entity to check
     * @return - If entity is part of any group
     */
    public boolean isInAnyGroup(final UUID entity) {
        return this.islandGroups.values().stream().anyMatch(ig -> ig.hasMember(entity));
    }

    /**
     * Get island group entity is part of
     * @param uuid - Entity UUID to retrieve group for
     * @return - Optional of IslandGroup object
     */
    public Optional<IslandGroup> getGroupForEntityUUID(final UUID uuid) {
        Optional<IslandGroup> result = islandGroups.values().stream().filter(g -> g.hasMember(uuid)).findFirst();
        if(result.isEmpty()) result = Optional.of(getDefaultGroup());
        return result;
    }

    /**
     * Retrieve list of island members
     */
    public List<UUID> getMembers() {
        return Collections.unmodifiableList(this.members);
    }

    /**
     * Remove member from island
     * @param entity - Entity to remove
     * @param id - Group UUID
     */
    public void removeMember(final UUID entity, final UUID id) {
        this.members.remove(entity);
        this.islandGroups.values().forEach(group -> group.removeMember(entity));
    }

    /**
     * Remove member from island group safely
     * @param entity - Entity to remove
     * @param id - Group UUID
     */
    public void removeGroupMember(final UUID entity, final UUID id) {
        final IslandGroup group = this.islandGroups.get(id);
        if(group == null) return;

        group.removeMember(entity);
        if(this.members.contains(entity) && !this.isInAnyGroup(entity)) {
            this.assignGroup(entity, SkyblockAddonCore.MOD_UUID);
        }
    }

    /**
     * Assign an entity to a permission group without changing island membership.
     *
     * @param entity Entity to assign
     * @param id Target group ID
     * @return If the group assignment was changed
     */
    public boolean assignGroup(final UUID entity, final UUID id) {
        if(getOwner().equals(entity)) return false;

        final IslandGroup group = this.islandGroups.get(id);
        if(group == null) return false;

        this.islandGroups.values().forEach(islandGroup -> islandGroup.removeMember(entity));
        group.addMember(entity);
        return true;
    }

    /**
     * Repairs recoverable membership inconsistencies from legacy or malformed data.
     * Custom-group assignments win over the default members group. The visitors
     * group is a fallback policy group and never stores canonical island members.
     */
    public MembershipRepairReport repairMembershipIntegrity() {
        int removedOwnerReferences = 0;
        int removedDuplicateMembers = 0;
        int recoveredMembers = 0;
        int repairedAssignments = 0;

        while (members.remove(getOwner())) removedOwnerReferences++;
        for (final IslandGroup group : islandGroups.values()) {
            while (group.hasMember(getOwner())) {
                group.removeMember(getOwner());
                removedOwnerReferences++;
            }
        }

        final LinkedHashSet<UUID> uniqueMembers = new LinkedHashSet<>();
        for (final UUID member : members) {
            if (member == null || !uniqueMembers.add(member)) removedDuplicateMembers++;
        }
        members.clear();
        members.addAll(uniqueMembers);

        final IslandGroup visitorsGroup = getDefaultGroup();
        if (visitorsGroup != null) {
            for (final UUID visitorReference : new ArrayList<>(visitorsGroup.getMembers())) {
                visitorsGroup.removeMember(visitorReference);
                repairedAssignments++;
            }
        }

        for (final IslandGroup group : islandGroups.values()) {
            if (group == visitorsGroup) continue;
            for (final UUID groupMember : new ArrayList<>(group.getMembers())) {
                if (groupMember == null || groupMember.equals(getOwner())) {
                    group.removeMember(groupMember);
                    repairedAssignments++;
                } else if (!members.contains(groupMember)) {
                    members.add(groupMember);
                    recoveredMembers++;
                }
            }
        }

        final IslandGroup membersGroup = getMembersGroup();
        if (membersGroup == null) {
            throw new IllegalStateException("Island " + getId() + " has no default members group");
        }

        for (final UUID member : new ArrayList<>(members)) {
            final List<IslandGroup> assignments = islandGroups.values().stream()
                    .filter(group -> group != visitorsGroup && group.hasMember(member))
                    .sorted(Comparator
                            .comparingInt((IslandGroup group) -> group == membersGroup ? 1 : 0)
                            .thenComparing(group -> group.getId().toString()))
                    .toList();
            final IslandGroup selectedGroup = assignments.isEmpty() ? membersGroup : assignments.get(0);

            for (final IslandGroup group : islandGroups.values()) {
                if (group != selectedGroup && group.hasMember(member)) {
                    group.removeMember(member);
                    repairedAssignments++;
                }
            }
            if (!selectedGroup.hasMember(member)) {
                selectedGroup.addMember(member);
                repairedAssignments++;
            }
        }

        return new MembershipRepairReport(
                removedOwnerReferences,
                removedDuplicateMembers,
                recoveredMembers,
                repairedAssignments
        );
    }

    public record MembershipRepairReport(
            int removedOwnerReferences,
            int removedDuplicateMembers,
            int recoveredMembers,
            int repairedAssignments
    ) {
        public boolean changed() {
            return removedOwnerReferences > 0
                    || removedDuplicateMembers > 0
                    || recoveredMembers > 0
                    || repairedAssignments > 0;
        }
    }

    /**
     * Safely add new member to island
     * Automatically runs owner & group checks
     * @param entity - Entity to add
     * @param id - Group ID
     * @return - If member has been added
     */
    public boolean addMember(final UUID entity, final UUID id) {
        if(getOwner().equals(entity)) return false;

        if(this.getOwner().equals(SkyblockAddonCore.MOD_UUID)) {
            setOwner(entity);
        } else {
            if(!this.assignGroup(entity, id)) return false;
            if(!this.members.contains(entity)) this.members.add(entity);
        }
        return true;
    }

    /**
     * Determine whether there are still members and/or an owner present on the island.
     * @return true - if abandoned
     */
    public boolean isAbandoned() {
        final boolean hasOwner = !this.getOwner().equals(SkyblockAddonCore.MOD_UUID);
        final boolean hasMembers = !this.getMembers().isEmpty();

        //If members found, but no owner we make a random member owner
        //Should not be possible but here to protect against edge cases.
        if(!hasOwner && hasMembers) this.getMembers().stream().findFirst().ifPresent(this::setOwner);

        return !hasOwner && !hasMembers;
    }

    /**
     * Get bounding box for island from center point.
     * @return BoundingBox of Island
     */
    public BoundingBox getIslandBoundingBox() {
        final int size = SkyblockAddonCore.ISLAND_SIZE;
        Vec3i min = new Vec3i(center.getX() - size, -450, center.getZ() - size);
        Vec3i max = new Vec3i(center.getX() + size, 450, center.getZ() + size);

        return BoundingBox.fromCorners(min, max);
    }

    /**
     * Get localized state of permission for group
     * @param id - Permission ID
     * @param groupId - Group UUID
     * @return - Localized string of permission state
     */
    public String getPermissionState(final String id, final UUID groupId) {
        return islandGroups.get(groupId).canDo(id) ? SkyBlockAddonLanguage.getLocalizedString("island.permission.enabled") : SkyBlockAddonLanguage.getLocalizedString("island.permission.disabled");
    }

    /**
     * Alter islands spawn point used for TeleportTo
     * @param point - New spawn point location
     */
    public void setSpawnPoint(final Vec3i point) {
        this.setSpawn(point.add(new Vec3i(0, 1, 0))); //Set it 0.5 blocks higher.
    }

    /**
     * Toggle the islands visibility from public/private
     */
    public void toggleVisibility() {
        this.setVisibility(!isVisible()); //Set it to inverse of its current.
    }

    /**
     * Determine if entity is part of the island.
     *
     * @param player - Entity whom to check
     * @return - Boolean
     */
    public boolean isPartOf(final UUID player) {
        return this.isOwner(player) || this.getMembers().contains(player);
    }

    /**
     * Determine if entity is owner of the island.
     *
     * @param uuid - Entity whom to check
     * @return - Boolean
     */
    public boolean isOwner(final UUID uuid) {
        return this.getOwner().equals(uuid);
    }

    /**
     * Simple getters & setters for island data
     */
    public IslandGroup getDefaultGroup() { return this.getGroup(SkyblockAddonCore.MOD_UUID2); }
    public IslandGroup getMembersGroup() { return this.getGroup(SkyblockAddonCore.MOD_UUID); }

    public UUID getId() {
        return id;
    }
    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getOwner() {
        if (owner == null) return SkyblockAddonCore.MOD_UUID;
        return owner;
    }
    public void setOwner(final UUID owner) {
        this.owner = owner;
    }

    public Vec3i getSpawn() {
        return spawn;
    }
    public void setSpawn(final Vec3i spawn) {
        this.spawn = spawn;
    }

    public Vec3i getCenter() {
        return center;
    }
    protected void setCenter(final Vec3i center) {
        this.center = center;
    }

    public String getBiome() {
        return biome;
    }
    public void setBiome(final String biome) {
        this.biome = biome;
    }

    public boolean isVisible() {
        return travelability;
    }
    public void setVisibility(final boolean travelability) {
        this.travelability = travelability;
    }

    public String getName() {
        triggerNameFetch();
        return name.isEmpty() ? "..." : name;
    }

    /**
     * Register a one-shot callback that fires when the owner name finishes loading.
     * If the name is already loaded the callback is NOT called — the current render
     * already has the correct value so no refresh is needed.
     */
    public void onNameLoaded(final Runnable callback) {
        if (!name.isEmpty()) return;
        nameCallbacks.add(callback);
        triggerNameFetch();
    }

    private void triggerNameFetch() {
        if (!name.isEmpty()) return;
        if (!nameFetching.compareAndSet(false, true)) return;
        UsernameCache.get(getOwner()).thenAccept(r -> {
            name = r;
            final List<Runnable> cbs = new ArrayList<>(nameCallbacks);
            nameCallbacks.clear();
            cbs.forEach(Runnable::run);
        });
    }

    public void updateName() {
        name = "";
        nameFetching.set(false);
    }

    /**
     * Chunk tracking
     */
    public List<ChunkRef> getLoadedChunks() {
        return Collections.unmodifiableList(loadedChunks);
    }

    public boolean addChunk(final ChunkRef ref) {
        if (loadedChunks.contains(ref)) return false;
        return loadedChunks.add(ref);
    }

    public void setChunks(final Collection<ChunkRef> chunks) {
        loadedChunks.clear();
        loadedChunks.addAll(chunks);
    }

    /**
     * Forge implementations
     */
    public abstract Square getIslandBoundingBoxAsSquare();
    public abstract Optional<UUID> getGroupByName(final String groupName);

    protected boolean removeChunk(ChunkRef chunkRef) {
        return loadedChunks.remove(chunkRef);
    }
}
