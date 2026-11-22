package yorickbm.skyblockaddon.islands;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import yorickbm.skyblockaddon.core.JSON.PermissionDataJson;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.EntityVerification;
import yorickbm.skyblockaddon.core.permissions.InteractionSubject;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.util.List;
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

    /**
     * A block placed at range by a world write no Forge event sees (Effortless Building build modes, modifiers, undo,
     * redo, quick replace and randomizer bag), judged by the island at the position like a placement by hand there.
     * Effortless Building drops and overwrites whatever stands at the position, so a block that is not replaceable also
     * needs the right to break it.
     */
    public static boolean denyRangedPlacement(final Player player, final Level level, final BlockPos position, final ItemStack placingStack, final BlockState placedState) {
        if (!(level instanceof final ServerLevel serverLevel)) return false;
        final Optional<Island> guardingIsland = islandGuardingRangedBuild(player, serverLevel, position);
        if (guardingIsland.isEmpty()) return false;

        final BlockState replacedState = serverLevel.getBlockState(position);
        final boolean overwritesBlock = !replacedState.isAir() && !replacedState.getMaterial().isReplaceable();
        final boolean denied = isDeniedOn(guardingIsland.get(), player, PermissionTriggers.ON_PLACE_BLOCK, InteractionHandler.subjectOfPlacedBlock(placingStack, placedState))
                || overwritesBlock && isDeniedOn(guardingIsland.get(), player, PermissionTriggers.ON_LEFT_CLICK_BLOCK,
                        InteractionHandler.subjectOfClick(serverLevel, position, player.getMainHandItem()));
        if (denied) InteractionHandler.sendDeniedMessage(player);
        return denied;
    }

    /**
     * A block broken at range by a world write no Forge event sees, judged by the island at the position like a break by
     * hand there (a left click with the main hand item).
     */
    public static boolean denyRangedBreak(final Player player, final Level level, final BlockPos position) {
        if (!(level instanceof final ServerLevel serverLevel)) return false;
        final Optional<Island> guardingIsland = islandGuardingRangedBuild(player, serverLevel, position);
        if (guardingIsland.isEmpty()) return false;

        final boolean denied = isDeniedOn(guardingIsland.get(), player, PermissionTriggers.ON_LEFT_CLICK_BLOCK,
                InteractionHandler.subjectOfClick(serverLevel, position, player.getMainHandItem()));
        if (denied) InteractionHandler.sendDeniedMessage(player);
        return denied;
    }

    /**
     * A batch of ranged placements (an Effortless Building build mode's start positions) is refused as a whole when any
     * position is denied for the stack the player places from. The Effortless Building client never writes the blocks
     * itself, so nothing needs resending.
     */
    public static boolean denyRangedPlacements(final Player player, final List<BlockPos> positions) {
        if (!(player.getLevel() instanceof final ServerLevel level)) return false;
        final ItemStack placingStack = InteractionHandler.placingStack(player);
        return positions.stream().anyMatch(position -> {
            final Optional<Island> guardingIsland = islandGuardingRangedBuild(player, level, position);
            if (guardingIsland.isEmpty()) return false;
            if (!isDeniedOn(guardingIsland.get(), player, PermissionTriggers.ON_PLACE_BLOCK, InteractionHandler.subjectOfClick(level, position, placingStack))) return false;
            InteractionHandler.sendDeniedMessage(player);
            return true;
        });
    }

    public static boolean denyRangedBreaks(final Player player, final List<BlockPos> positions) {
        return positions.stream().anyMatch(position -> denyRangedBreak(player, player.getLevel(), position));
    }

    private static boolean isDeniedOn(final Island island, final Player player, final String trigger, final InteractionSubject subject) {
        return InteractionHandler.isInteractionDenied(new AtomicReference<>(island), (ServerPlayer) player, trigger, subject);
    }

    /**
     * The island whose permissions judge a build at the position, or empty when nothing is checked: not a real player on
     * the server, outside the overworld, a server admin, no island there, or the player owns it.
     */
    private static Optional<Island> islandGuardingRangedBuild(final Player player, final ServerLevel level, final BlockPos position) {
        if (!(player instanceof final ServerPlayer serverPlayer) || player instanceof FakePlayer) return Optional.empty();
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        if (serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return Optional.empty();

        final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.forgeToInternalVec3i(position));
        if (island == null || island.isOwner(serverPlayer.getUUID())) return Optional.empty();
        return Optional.of(island);
    }

    private static void sendRateLimitedPortalMessage(final ServerPlayer player, final long gameTime) {
        final Long lastMessageGameTime = LAST_DENIED_PORTAL_MESSAGE_GAME_TIME.get(player);
        if (lastMessageGameTime != null && gameTime - lastMessageGameTime < DENIED_PORTAL_MESSAGE_INTERVAL_TICKS) return;

        LAST_DENIED_PORTAL_MESSAGE_GAME_TIME.put(player, gameTime);
        InteractionHandler.sendDeniedMessage(player);
    }
}
