package yorickbm.skyblockaddon.core.islands;

import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;

import java.util.*;

public abstract class IslandGroup {

    protected UUID uuid;
    protected final Map<String, Boolean> permissions = new HashMap<>();
    protected final List<UUID> members = new ArrayList<>();

    public IslandGroup() {
        this.uuid = UUID.randomUUID();
        PermissionManager.getInstance().getPermissions().forEach(p -> {
            this.permissions.put(p.getId(), p.getData().getDefault());
        });
    }

    public IslandGroup(final UUID uuid, final boolean allowAll) {
        this.uuid = uuid;

        PermissionManager.getInstance().getPermissions().forEach(p -> {
            this.permissions.put(p.getId(), p.getData().getDefault() || allowAll);
        });
    }

    /**
     * Determine if group may execute permission
     * @param id Permission ID
     * @return If permission can be done by group
     */
    public boolean canDo(final String id) {
        if(!this.permissions.containsKey(id)) {
            Optional<Permission> perm = PermissionManager.getInstance().getPermissions().stream().filter(p -> p.getId().equalsIgnoreCase(id)).findFirst();
            if(perm.isEmpty()) return false;

            //Add permission into list for session with default.
            this.permissions.put(id, perm.get().getData().getDefault());
            return perm.get().getData().getDefault();
        }
        return this.permissions.get(id);
    }

    /**
     * Add member to group
     * @param entity Entity UUID
     */
    public void addMember(final UUID entity) {
        if(this.members.contains(entity)) return;
        this.members.add(entity);
    }

    /**
     * Remove member from group
     * @param entity Entity UUID
     */
    public void removeMember(final UUID entity) {
        this.members.remove(entity);
    }

    /**
     * Determine if entity is part of group
     * @param entity Entity UUID
     * @return If entity is within the groups members
     */
    public boolean hasMember(final UUID entity) {
        return this.members.contains(entity);
    }

    /**
     * Simple getters & setters
     */
    public UUID getId() {
        return this.uuid;
    }
    public List<UUID> getMembers() {
        return this.members;
    }

    public void setPermission(final String id, final boolean value) {
        this.permissions.put(id, value);
    }
    public void inversePermission(final String id) {
        this.setPermission(id, !this.canDo(id));
    }

    /**
     * Forge implementations
     */
    public abstract String getName();
}
