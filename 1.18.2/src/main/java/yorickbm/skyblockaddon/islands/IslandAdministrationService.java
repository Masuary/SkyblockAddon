package yorickbm.skyblockaddon.islands;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.skyblockaddon.core.events.IslandDataUpdateEvent;
import yorickbm.skyblockaddon.core.events.IslandEventBus;
import yorickbm.skyblockaddon.core.events.IslandGroupUpdateEvent;
import yorickbm.skyblockaddon.core.events.IslandMemberUpdateEvent;
import yorickbm.skyblockaddon.core.events.PermissionUpdateEvent;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.util.UUID;

/**
 * Authoritative mutation boundary shared by inventory GUIs and MasuGUI.
 * Every operation revalidates authorization at execution time and emits the
 * same public island events regardless of the GUI used to invoke it.
 */
public final class IslandAdministrationService {
    private IslandAdministrationService() { }

    public static boolean canManage(final ServerPlayer player, final Island island) {
        if (player == null || island == null) return false;
        if (player.hasPermissions(Commands.LEVEL_ADMINS) || island.isOwner(player.getUUID())) return true;
        return island.getGroupForEntityUUID(player.getUUID())
                .map(group -> group.canDo("admin_menu"))
                .orElse(false);
    }

    public static boolean toggleVisibility(final ServerPlayer actor, final Island island) {
        if (!canManage(actor, island)) return false;
        final boolean oldVisibility = island.isVisible();
        island.toggleVisibility();
        IslandEventBus.fire(new IslandDataUpdateEvent(
                island,
                IslandDataUpdateEvent.Field.VISIBILITY,
                oldVisibility,
                island.isVisible()
        ));
        return true;
    }

    public static boolean updateBiome(
            final ServerPlayer actor,
            final ForgeIsland island,
            final String biomeId
    ) {
        if (!canManage(actor, island) || biomeId == null || biomeId.isBlank()) return false;
        final String oldBiome = island.getBiome();
        island.updateBiome(biomeId, actor.getLevel());
        if (!biomeId.equals(island.getBiome())) return false;
        IslandEventBus.fire(new IslandDataUpdateEvent(
                island,
                IslandDataUpdateEvent.Field.BIOME,
                oldBiome,
                island.getBiome()
        ));
        return true;
    }

    public static boolean setSpawn(final ServerPlayer actor, final ForgeIsland island) {
        if (!canManage(actor, island)) return false;
        final Vec3i position = ForgeConverter.ForgeToInternalVec3i(actor.blockPosition());
        if (!island.getIslandBoundingBox().isInside(position)) return false;

        final Vec3i oldSpawn = island.getSpawn();
        island.setSpawnPoint(position);
        IslandEventBus.fire(new IslandDataUpdateEvent(
                island,
                IslandDataUpdateEvent.Field.SPAWNPOINT,
                oldSpawn,
                island.getSpawn()
        ));
        return true;
    }

    public static boolean assignMemberGroup(
            final ServerPlayer actor,
            final Island island,
            final UUID memberId,
            final UUID groupId
    ) {
        if (!canManage(actor, island) || !island.getMembers().contains(memberId)) return false;
        if (!island.assignGroup(memberId, groupId)) return false;
        IslandEventBus.fire(new IslandMemberUpdateEvent(
                island,
                memberId,
                IslandMemberUpdateEvent.Action.GROUP_CHANGED
        ));
        return true;
    }

    public static boolean setPermission(
            final ServerPlayer actor,
            final Island island,
            final UUID groupId,
            final String permissionId,
            final boolean enabled
    ) {
        if (!canManage(actor, island)) return false;
        final IslandGroup group = island.getGroup(groupId);
        if (group == null || PermissionManager.getInstance().getPermissions().stream()
                .noneMatch(permission -> permission.getId().equalsIgnoreCase(permissionId))) return false;

        final PermissionUpdateEvent event = IslandEventBus.fire(new PermissionUpdateEvent(
                island,
                groupId,
                permissionId,
                enabled,
                actor.getUUID()
        ));
        if (event.isCancelled()) return false;
        group.setPermission(permissionId, event.isEnabled());
        return true;
    }

    public static boolean createGroup(
            final ServerPlayer actor,
            final Island island,
            final ItemStack icon
    ) {
        if (!canManage(actor, island) || icon == null || icon.isEmpty() || icon.getItem() == Items.AIR) return false;
        final String groupName = icon.getDisplayName().getString().trim();
        if (groupName.isEmpty() || island.getGroups().stream()
                .anyMatch(group -> group.getName().equalsIgnoreCase(groupName))) return false;

        final UUID groupId = UUID.randomUUID();
        island.addGroup(new ForgeIslandGroup(groupId, icon.copy(), false));
        IslandEventBus.fire(new IslandGroupUpdateEvent(
                island,
                groupId,
                groupName,
                IslandGroupUpdateEvent.Action.CREATED
        ));
        return true;
    }

    public static boolean removeGroup(
            final ServerPlayer actor,
            final Island island,
            final UUID groupId
    ) {
        if (!canManage(actor, island)) return false;
        final IslandGroup group = island.getGroup(groupId);
        if (group == null) return false;
        final String groupName = group.getName();
        if (!island.removeGroup(groupId)) return false;
        IslandEventBus.fire(new IslandGroupUpdateEvent(
                island,
                groupId,
                groupName,
                IslandGroupUpdateEvent.Action.REMOVED
        ));
        return true;
    }
}
