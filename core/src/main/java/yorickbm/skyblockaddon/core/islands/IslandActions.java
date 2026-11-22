package yorickbm.skyblockaddon.core.islands;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.UUID;

/**
 * The island actions shared by both GUIs and the commands. Every action checks the actor's rights first and only
 * changes the island when it returns {@link IslandActionResult#SUCCESS}.
 */
public final class IslandActions {
    public static final String ADMIN_MENU_PERMISSION = "admin_menu";

    private IslandActions() {
    }

    /**
     * The owner, a member whose group has the admin_menu permission, or an operator may manage the island.
     */
    public static boolean canManage(final Island island, final IslandActor actor) {
        if (actor.operator() || island.isOwner(actor.playerId())) return true;
        if (!island.getMembers().contains(actor.playerId())) return false;
        return island.getGroupForEntityUUID(actor.playerId())
                .map(group -> group.canDo(ADMIN_MENU_PERMISSION))
                .orElse(false);
    }

    public static IslandActionResult assignGroup(final Island island, final IslandActor actor, final UUID target, final UUID groupId) {
        if (!canManage(island, actor)) return IslandActionResult.NOT_ALLOWED;
        if (!island.hasGroup(groupId)) return IslandActionResult.GROUP_NOT_FOUND;
        if (island.isOwner(target)) return IslandActionResult.TARGET_IS_OWNER;
        if (island.isBanned(target)) return IslandActionResult.TARGET_IS_BANNED;
        if (island.isDemotionToVisitors(target, groupId)) return removeDemotedMember(island, actor, target);

        final boolean assigned = island.isPartOf(target)
                ? island.addMember(target, groupId)
                : island.assignGroup(target, groupId);
        return assigned ? IslandActionResult.SUCCESS : IslandActionResult.NO_CHANGE;
    }

    /**
     * Moving a member to Visitors removes them from the island like a kick, so a manager cannot do it to themselves
     * (they leave with /island leave, which can be undone).
     */
    private static IslandActionResult removeDemotedMember(final Island island, final IslandActor actor, final UUID target) {
        if (target.equals(actor.playerId())) return IslandActionResult.TARGET_IS_SELF;
        island.removeFromIsland(target);
        return IslandActionResult.MEMBER_REMOVED;
    }

    public static IslandActionResult createGroup(final Island island, final IslandActor actor, final IslandGroup group) {
        if (!canManage(island, actor)) return IslandActionResult.NOT_ALLOWED;
        if (island.getGroupByName(group.getName()).isPresent()) return IslandActionResult.GROUP_NAME_EXISTS;

        island.addGroup(group);
        return IslandActionResult.SUCCESS;
    }

    public static IslandActionResult removeGroup(final Island island, final IslandActor actor, final UUID groupId) {
        if (!canManage(island, actor)) return IslandActionResult.NOT_ALLOWED;
        if (isDefaultGroup(groupId)) return IslandActionResult.DEFAULT_GROUP_PROTECTED;
        if (!island.hasGroup(groupId)) return IslandActionResult.GROUP_NOT_FOUND;
        return island.removeGroup(groupId) ? IslandActionResult.SUCCESS : IslandActionResult.NO_CHANGE;
    }

    public static IslandActionResult toggleVisibility(final Island island, final IslandActor actor) {
        if (!canManage(island, actor)) return IslandActionResult.NOT_ALLOWED;
        island.toggleVisibility();
        return IslandActionResult.SUCCESS;
    }

    public static IslandActionResult kickMember(final Island island, final IslandActor actor, final UUID target) {
        if (!canManage(island, actor)) return IslandActionResult.NOT_ALLOWED;
        if (island.isOwner(target)) return IslandActionResult.TARGET_IS_OWNER;
        if (target.equals(actor.playerId())) return IslandActionResult.TARGET_IS_SELF;
        if (!island.getMembers().contains(target)) return removeGuestFromGroups(island, target);

        island.removeFromIsland(target);
        return IslandActionResult.SUCCESS;
    }

    private static IslandActionResult removeGuestFromGroups(final Island island, final UUID guest) {
        if (!island.isInAnyGroup(guest)) return IslandActionResult.TARGET_NOT_MEMBER;
        island.getGroups().forEach(group -> group.removeMember(guest));
        return IslandActionResult.SUCCESS;
    }

    public static IslandActionResult checkTransferOwnership(final Island island, final IslandActor actor, final UUID newOwner) {
        if (!island.isOwner(actor.playerId())) return IslandActionResult.OWNER_ONLY;
        if (newOwner.equals(actor.playerId())) return IslandActionResult.TARGET_IS_SELF;
        if (!island.getMembers().contains(newOwner)) return IslandActionResult.TARGET_NOT_MEMBER;
        return IslandActionResult.SUCCESS;
    }

    public static IslandActionResult transferOwnership(final Island island, final IslandActor actor, final UUID newOwner) {
        final IslandActionResult check = checkTransferOwnership(island, actor, newOwner);
        if (!check.succeeded()) return check;
        return island.transferOwnershipTo(newOwner) ? IslandActionResult.SUCCESS : IslandActionResult.NO_CHANGE;
    }

    public static IslandActionResult banPlayer(final Island island, final IslandActor actor, final UUID target) {
        if (!canManage(island, actor)) return IslandActionResult.NOT_ALLOWED;
        if (target.equals(actor.playerId())) return IslandActionResult.TARGET_IS_SELF;
        if (island.isPartOf(target)) return IslandActionResult.TARGET_IS_MEMBER;
        if (island.isBanned(target)) return IslandActionResult.TARGET_IS_BANNED;
        return island.banPlayer(target) ? IslandActionResult.SUCCESS : IslandActionResult.NO_CHANGE;
    }

    public static IslandActionResult unbanPlayer(final Island island, final IslandActor actor, final UUID target) {
        if (!canManage(island, actor)) return IslandActionResult.NOT_ALLOWED;
        if (!island.isBanned(target)) return IslandActionResult.TARGET_NOT_BANNED;
        island.unbanPlayer(target);
        return IslandActionResult.SUCCESS;
    }

    /**
     * Whether the actor may send the target off the island. Where the target stands is checked by the caller.
     */
    public static IslandActionResult checkExpel(final Island island, final IslandActor actor, final UUID target) {
        if (!canManage(island, actor)) return IslandActionResult.NOT_ALLOWED;
        if (target.equals(actor.playerId())) return IslandActionResult.TARGET_IS_SELF;
        if (island.isPartOf(target)) return IslandActionResult.TARGET_IS_MEMBER;
        return IslandActionResult.SUCCESS;
    }

    public static boolean isDefaultGroup(final UUID groupId) {
        return groupId.equals(SkyblockAddonCore.MEMBERS_GROUP_ID) || groupId.equals(SkyblockAddonCore.VISITORS_GROUP_ID);
    }
}
