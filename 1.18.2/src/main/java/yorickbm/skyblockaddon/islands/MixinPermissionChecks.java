package yorickbm.skyblockaddon.islands;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import yorickbm.skyblockaddon.core.permissions.EntityVerification;
import yorickbm.skyblockaddon.core.permissions.InteractionSubject;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;

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
     * Blocks that hand out or take items on right click (Vault treasure pedestals, Buildscape pillars) and are reached
     * without a cancellable event: fake players never get the items, a denied player gets FAIL. Empty when the block's
     * own use should run.
     */
    public static Optional<InteractionResult> itemHolderUseOverride(final Player player, final Level level, final BlockPos position, final InteractionHand hand) {
        if (player instanceof FakePlayer) return Optional.of(InteractionResult.SUCCESS);
        if (!(player instanceof final ServerPlayer serverPlayer) || !(level instanceof final ServerLevel serverLevel)) return Optional.empty();

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return Optional.empty();
        if (!InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer, serverLevel, position, player.getItemInHand(hand), PermissionTriggers.ON_RIGHT_CLICK_BLOCK)) {
            return Optional.empty();
        }

        InteractionHandler.sendDeniedMessage(player);
        return Optional.of(InteractionResult.FAIL);
    }

    /**
     * A block placed at range by a world write no Forge event sees (Effortless Building build modes, modifiers, undo,
     * redo, quick replace and randomizer bag), judged by the island at the position like a placement by hand there.
     * Effortless Building drops and overwrites whatever stands at the position, so a block that is not replaceable also
     * needs the right to break it.
     */
    public static boolean denyRangedPlacement(final Player player, final Level level, final BlockPos position, final ItemStack placingStack, final BlockState placedState) {
        if (!(level instanceof final ServerLevel serverLevel)) return false;
        final Optional<Island> guardingIsland = InteractionHandler.islandGuardingPosition(player, serverLevel, position);
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
     * A click on a block of an assembled Create contraption (storage, deployers, doors, levers, train controls) reaches
     * the block through Create's own packet, without a Forge event: judged like a right click on the same block placed
     * at the contraption block's world position, by the island there.
     */
    public static boolean denyContraptionBlockClick(final Player player, final Level level, final BlockPos worldPosition, final BlockState contraptionBlock, final ItemStack handItem) {
        if (!(level instanceof final ServerLevel serverLevel)) return false;
        final Optional<Island> guardingIsland = InteractionHandler.islandGuardingPosition(player, serverLevel, worldPosition);
        if (guardingIsland.isEmpty()) return false;

        final boolean denied = isDeniedOn(guardingIsland.get(), player, PermissionTriggers.ON_RIGHT_CLICK_BLOCK, InteractionHandler.subjectOfClickOn(contraptionBlock, handItem));
        if (denied && InteractionHandler.isFirstDenialMessageThisTick((ServerPlayer) player)) InteractionHandler.sendDeniedMessage(player);
        return denied;
    }

    /**
     * PneumaticCraft's wrench packet for another mod's wrench, judged by the island at the position: a wrenched drone
     * like a right click on it, a sneaking wrench (the block is dismantled and dropped) like breaking the block, else
     * (the block is rotated) like a right click on the block with the wrench.
     */
    public static boolean denyPneumaticCraftWrench(final Player player, final Level level, final BlockPos position, final Object wrenched, final InteractionHand hand) {
        if (!(level instanceof final ServerLevel serverLevel) || !(player instanceof final ServerPlayer serverPlayer)) return false;
        final ItemStack wrench = player.getItemInHand(hand);
        final boolean denied;
        if (wrenched instanceof final Entity wrenchedEntity) {
            denied = InteractionHandler.isEntityInteractionDenied(serverPlayer, wrenchedEntity, wrench);
        } else if (player.isShiftKeyDown()) {
            denied = InteractionHandler.isBreakDenied(player, serverLevel, position);
        } else {
            denied = InteractionHandler.islandGuardingPosition(player, serverLevel, position)
                    .map(island -> isDeniedOn(island, player, PermissionTriggers.ON_RIGHT_CLICK_BLOCK, InteractionHandler.subjectOfClick(serverLevel, position, wrench)))
                    .orElse(false);
        }
        if (denied && InteractionHandler.isFirstDenialMessageThisTick(serverPlayer)) InteractionHandler.sendDeniedMessage(player);
        return denied;
    }

    /**
     * A block broken at range by a world write no Forge event sees, judged by the island at the position like a break by
     * hand there (a left click with the main hand item).
     */
    public static boolean denyRangedBreak(final Player player, final Level level, final BlockPos position) {
        if (!(level instanceof final ServerLevel serverLevel)) return false;
        final Optional<Island> guardingIsland = InteractionHandler.islandGuardingPosition(player, serverLevel, position);
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
            final Optional<Island> guardingIsland = InteractionHandler.islandGuardingPosition(player, level, position);
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

    private static void sendRateLimitedPortalMessage(final ServerPlayer player, final long gameTime) {
        final Long lastMessageGameTime = LAST_DENIED_PORTAL_MESSAGE_GAME_TIME.get(player);
        if (lastMessageGameTime != null && gameTime - lastMessageGameTime < DENIED_PORTAL_MESSAGE_INTERVAL_TICKS) return;

        LAST_DENIED_PORTAL_MESSAGE_GAME_TIME.put(player, gameTime);
        InteractionHandler.sendDeniedMessage(player);
    }
}
