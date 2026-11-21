package yorickbm.skyblockaddon.islands;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.core.JSON.PermissionDataJson;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.permissions.EntityVerification;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Permission checks for mod and vanilla code reached through mixins, where no Forge event exists to cancel.
 */
public final class MixinPermissionChecks {
    private static final long DENIED_PORTAL_MESSAGE_INTERVAL_TICKS = 40;
    private static final Map<ServerPlayer, Long> LAST_DENIED_PORTAL_MESSAGE_GAME_TIME = new WeakHashMap<>();

    private MixinPermissionChecks() {
    }

    /**
     * Portal blocks call this every tick a player stands in them: a denied player is pushed back, the portal timer is
     * skipped by the caller, and the message is rate limited.
     */
    public static boolean denyPortalEntry(final Entity entity, final Level level, final BlockPos portalPosition, final String trigger) {
        if (!(entity instanceof final ServerPlayer player) || !(level instanceof final ServerLevel serverLevel)) return false;

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        final EntityVerification verification = serverLevel.dimension() == Level.NETHER
                ? InteractionHandler.verifyNetherEntity(player, standingOn, portalPosition)
                : InteractionHandler.verifyEntity(player, standingOn);
        if (verification.asBoolean()) return false;
        if (!InteractionHandler.isInteractionDenied(standingOn, player, serverLevel, portalPosition, ItemStack.EMPTY, trigger)) return false;

        sendRateLimitedPortalMessage(player, serverLevel.getGameTime());
        InteractionHandler.pushPlayerBack(player);
        player.resetFallDistance();
        return true;
    }

    public static boolean denyElevatorUse(final ServerPlayer player) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return false;
        if (!InteractionHandler.isInteractionDenied(standingOn, player, player.getLevel(), player.getOnPos(), ItemStack.EMPTY, PermissionTriggers.ON_ELEVATOR_USE)) return false;

        InteractionHandler.sendDeniedMessage(player);
        return true;
    }

    /**
     * Picking up a mob into an item (Easy Villagers, Easy Piglins): the mob's type is matched against the entity data
     * of the trigger's permissions.
     */
    public static boolean denyMobPickup(final ServerPlayer player, final EntityType<?> pickedUpType, final String trigger) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return false;

        final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(player.getUUID());
        final String pickedUpTypeId = Objects.requireNonNull(EntityType.getKey(pickedUpType)).toString();
        if (group.isPresent() && !InteractionHandler.isDeniedByMatchData(group, trigger, pickedUpTypeId, PermissionDataJson::getEntitiesData, true)) return false;

        InteractionHandler.sendDeniedMessage(player);
        return true;
    }

    private static void sendRateLimitedPortalMessage(final ServerPlayer player, final long gameTime) {
        final Long lastMessageGameTime = LAST_DENIED_PORTAL_MESSAGE_GAME_TIME.get(player);
        if (lastMessageGameTime != null && gameTime - lastMessageGameTime < DENIED_PORTAL_MESSAGE_INTERVAL_TICKS) return;

        LAST_DENIED_PORTAL_MESSAGE_GAME_TIME.put(player, gameTime);
        InteractionHandler.sendDeniedMessage(player);
    }
}
