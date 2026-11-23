package yorickbm.skyblockaddon.islands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.SkyBlockAddon;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.core.JSON.PermissionDataJson;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.EntityVerification;
import yorickbm.skyblockaddon.core.permissions.InteractionPermissionDecision;
import yorickbm.skyblockaddon.core.permissions.InteractionSubject;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.core.util.MatchResult;
import yorickbm.skyblockaddon.util.EntityPlacerItems;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

public class InteractionHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<ServerPlayer, Long> LAST_DENIAL_MESSAGE_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_INVENTORY_RESYNC_TICK = new WeakHashMap<>();

    public static void deny(final Event event, final Player player) {
        if (event.isCancelable()) event.setCanceled(true);
        else event.setResult(Event.Result.DENY);
        sendDeniedMessage(player);
    }

    public static void sendDeniedMessage(final Player player) {
        player.displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
    }

    /**
     * Area tools and held clicks deny many actions per tick; the denial message and the inventory resync each only need
     * to go out once per player and tick. Server thread only.
     */
    public static boolean isFirstDenialMessageThisTick(final ServerPlayer player) {
        return isFirstThisTick(LAST_DENIAL_MESSAGE_TICK, player);
    }

    public static boolean isFirstInventoryResyncThisTick(final ServerPlayer player) {
        return isFirstThisTick(LAST_INVENTORY_RESYNC_TICK, player);
    }

    private static boolean isFirstThisTick(final Map<ServerPlayer, Long> lastTickByPlayer, final ServerPlayer player) {
        final long gameTime = player.getLevel().getGameTime();
        final Long lastTick = lastTickByPlayer.put(player, gameTime);
        return lastTick == null || lastTick != gameTime;
    }

    public static void pushPlayerBack(final ServerPlayer player) {
        final double radianYaw = Math.toRadians(player.getYRot());
        final Vec3 direction = new Vec3(Math.sin(radianYaw), 0, -Math.cos(radianYaw));
        player.setDeltaMovement(new Vec3(direction.x * 0.4, 0.28, direction.z * 0.4));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /**
     * Match a value (item, entity or dimension id) against the data of every permission on the trigger that the group
     * lacks. A lacked permission without data denies outright.
     */
    public static boolean isDeniedByMatchData(final Optional<IslandGroup> group, final String trigger, final String matchValue, final Function<PermissionDataJson, List<String>> dataExtractor, final boolean logDecision) {
        if(logDecision) SkyBlockAddon.logPermissionDebug(LOGGER, trigger);

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger(trigger);
        if (perms.isEmpty()) {
            if(logDecision) SkyBlockAddon.logPermissionDebug(LOGGER, "No permissions found for trigger (ALLOWED)");
            return false;
        }
        if(group.isEmpty()) return false;

        boolean runFail = false;
        for (final Permission perm : perms) {
            if (group.get().canDo(perm.getId())) {
                if(logDecision) SkyBlockAddon.logPermissionDebug(LOGGER, "action is ALLOWED on " + perm.getId() + " in group " + group.get().getName());
                continue;
            }
            if (runFail) break;

            final List<String> data = dataExtractor.apply(perm.getData());
            if (data.isEmpty()) {
                if(logDecision) SkyBlockAddon.logPermissionDebug(LOGGER, "action is BLOCKED on " + perm.getId() + " in group " + group.get().getName());
                runFail = true;
            } else {
                final MatchResult rslt = PermissionManager.checkMatch(data, matchValue);
                if(logDecision) SkyBlockAddon.logPermissionDebug(LOGGER, matchValue + " is " + rslt + " on " + perm.getId() + " in group " + group.get().getName());
                if (rslt == MatchResult.BLOCK) runFail = true;
            }
        }
        return runFail;
    }

    /**
     * Run requirement check for entity. This check determines if it may override on the event.
     * Or if a permission check should be performed.
     *
     * @param entity - Triggering entity
     * @param standingOn - AtomicReference to get Island entity is standing on.
     * @return - True if entity may override permission check.
     */
    public static EntityVerification verifyEntity(final Entity entity, final AtomicReference<Island> standingOn) {
        record Verifier(Entity entity, AtomicReference<Island> standingOn) {
            EntityVerification verify() {
                if (!(entity instanceof final ServerPlayer player) || entity instanceof FakePlayer)
                    return EntityVerification.NOT_A_PLAYER;
                if (player.getLevel().dimension() != Level.OVERWORLD)
                    return EntityVerification.NOT_IN_OVERWORLD;
                if (player.hasPermissions(Commands.LEVEL_ADMINS))
                    return EntityVerification.IS_ADMIN;

                final Optional<SkyblockAddonWorldCapability> cap =
                        player.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).resolve();
                if (cap.isEmpty())
                    return EntityVerification.CAP_NOT_FOUND;

                final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.forgeToInternalVec3i(player.getOnPos()));
                if (island == null)
                    return EntityVerification.NOT_ON_ISLAND;

                standingOn.set(island);
                return island.isOwner(player.getUUID()) ? EntityVerification.IS_ISLAND_OWNER : EntityVerification.IS_ISLAND_MEMBER;
            }
        }

        final EntityVerification result = new Verifier(entity, standingOn).verify();
        return result;
    }

    /**
     * Run requirement check for entity. This check determines if it may override on the event.
     * Or if a permission check should be performed.
     *
     * @param entity - Triggering entity
     * @param standingOn - AtomicReference to get Island entity is standing on.
     * @return - True if entity may override permission check.
     */
    public static EntityVerification verifyNetherEntity(final Entity entity, final AtomicReference<Island> standingOn, final BlockPos triggerPoint) {
        if(!(entity instanceof final ServerPlayer player) || entity instanceof FakePlayer) return EntityVerification.NOT_A_PLAYER; //Allowed types
        if(player.getLevel().dimension() != Level.NETHER) return EntityVerification.NOT_IN_NETHER; //Is not in nether
        if(player.hasPermissions(Commands.LEVEL_ADMINS)) return EntityVerification.IS_ADMIN; //Player is admin;

        final Optional<SkyblockAddonWorldCapability> cap = Objects.requireNonNull(Objects.requireNonNull(player.getServer()).getLevel(Level.OVERWORLD)).getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).resolve();
        if(cap.isEmpty()) return EntityVerification.CAP_NOT_FOUND; //Could not find Capability


        final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.forgeToInternalVec3i(new BlockPos(triggerPoint.getX() * 8, triggerPoint.getY(), triggerPoint.getZ() * 8)));
        if(island == null) return EntityVerification.NOT_ON_ISLAND; //Not within protection

        standingOn.set(island); //Set atomic reference
        return island.isOwner(player.getUUID()) ? EntityVerification.IS_ISLAND_OWNER : EntityVerification.IS_ISLAND_MEMBER; //Owners may do anything
    }

    /**
     * Decide whether the interaction is denied; on denial the block at the position is resent to the player so the
     * client drops any predicted change.
     *
     * @param standingOn - Island player is standing on
     * @param trigger - Trigger type to use
     */
    public static boolean checkPlayerInteraction(final AtomicReference<Island> standingOn, final ServerPlayer player, final ServerLevel world, final BlockPos position, final ItemStack handItem, final String trigger) {
        return checkPlayerInteraction(standingOn, player, world, position, trigger, subjectOfClick(world, position, handItem));
    }

    public static boolean checkPlayerInteraction(final AtomicReference<Island> standingOn, final ServerPlayer player, final ServerLevel world, final BlockPos position, final String trigger, final InteractionSubject subject) {
        final boolean denied = isInteractionDenied(standingOn, player, trigger, subject);
        if (denied) {
            resendBlockToPlayer(player, world, position);
        }
        return denied;
    }

    public static void resendBlockToPlayer(final ServerPlayer player, final ServerLevel world, final BlockPos position) {
        final BlockState clickedState = world.getBlockState(position);
        ServerHelper.sendPacket(player, new ClientboundBlockUpdatePacket(position, clickedState));

        if (clickedState.getBlock() instanceof DoorBlock) {
            final BlockPos otherHalf = clickedState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? position.above() : position.below();
            ServerHelper.sendPacket(player, new ClientboundBlockUpdatePacket(otherHalf, world.getBlockState(otherHalf)));
        }

        final BlockEntity blockEntity = world.getBlockEntity(position);
        if (blockEntity != null) {
            final Packet<ClientGamePacketListener> blockEntityUpdate = blockEntity.getUpdatePacket();
            if (blockEntityUpdate != null) ServerHelper.sendPacket(player, blockEntityUpdate);
        }
    }

    /**
     * Deny a right-click on a block and close any screen the click may have opened.
     */
    public static void denyBlockClick(final PlayerInteractEvent.RightClickBlock event, final ServerPlayer player) {
        deny(event, player);
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
    }

    public static boolean isInteractionDenied(final AtomicReference<Island> standingOn, final ServerPlayer player, final ServerLevel world, final BlockPos position, final ItemStack handItem, final String trigger) {
        return isInteractionDenied(standingOn, player, trigger, subjectOfClick(world, position, handItem));
    }

    public static boolean isInteractionDenied(final AtomicReference<Island> standingOn, final ServerPlayer player, final String trigger, final InteractionSubject subject) {
        SkyBlockAddon.logPermissionDebug(LOGGER, trigger);

        final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(player.getUUID());
        if(group.isEmpty()) {
            return true;
        }

        final Optional<Permission> denyingPermission = InteractionPermissionDecision.findDenyingPermission(
                trigger, PermissionManager.getInstance().getPermissionsForTrigger(trigger), group.get()::canDo, subject);
        denyingPermission.ifPresent(permission -> SkyBlockAddon.logPermissionDebug(LOGGER, subject + " is BLOCKED on " + permission.getId() + " in group " + group.get().getName()));
        return denyingPermission.isPresent();
    }

    /**
     * The island whose permissions judge an action at the position, or empty when nothing is checked: not a real player
     * on the server, outside the overworld, a server admin, no island there, or the player owns it.
     */
    public static Optional<Island> islandGuardingPosition(final Player player, final ServerLevel level, final BlockPos position) {
        if (!(player instanceof final ServerPlayer serverPlayer) || player instanceof FakePlayer) return Optional.empty();
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        if (serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return Optional.empty();

        final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.forgeToInternalVec3i(position));
        if (island == null || island.isOwner(serverPlayer.getUUID())) return Optional.empty();
        return Optional.of(island);
    }

    /**
     * A right click on an entity, judged by the island the entity is on: the entity's type id against the entity rules
     * of the onRightClickEntity permissions, and the hand item against their item rules.
     */
    public static boolean isEntityInteractionDenied(final ServerPlayer player, final Entity target, final ItemStack handItem) {
        if (!(target.getLevel() instanceof final ServerLevel level)) return false;
        final Optional<Island> guardingIsland = islandGuardingPosition(player, level, target.blockPosition());
        if (guardingIsland.isEmpty()) return false;

        final Optional<IslandGroup> group = guardingIsland.get().getGroupForEntityUUID(player.getUUID());
        if (group.isEmpty()) return true;

        final String entityTypeId = Objects.requireNonNull(EntityType.getKey(target.getType())).toString();
        final Optional<Permission> denyingPermission = InteractionPermissionDecision.findDenyingEntityInteraction(
                PermissionManager.getInstance().getPermissionsForTrigger(PermissionTriggers.ON_RIGHT_CLICK_ENTITY), group.get()::canDo, entityTypeId, itemId(handItem));
        denyingPermission.ifPresent(permission -> SkyBlockAddon.logPermissionDebug(LOGGER, entityTypeId + " (entity click) is BLOCKED on " + permission.getId() + " in group " + group.get().getName()));
        return denyingPermission.isPresent();
    }

    /**
     * A storage screen whose slots belong to an entity (a chest riding a boat, a ship's hold, a chest minecart) can open
     * without any click on that entity: Quark's seated inventory key opens the chest on the boat a player rides, and the
     * Small Ships open-screen packet opens any ship nearby. The screen is judged like a right click on the entity, on
     * the island where the entity is.
     */
    public static boolean isEntityStorageDenied(final ServerPlayer player, final AbstractContainerMenu menu) {
        return menu.slots.stream()
                .map(slot -> slot.container)
                .filter(Entity.class::isInstance)
                .map(Entity.class::cast)
                .filter(entity -> !(entity instanceof Player))
                .distinct()
                .anyMatch(entity -> isEntityInteractionDenied(player, entity, ItemStack.EMPTY));
    }

    /**
     * Any block break by a player, whatever caused it (a left click, an area tool, a spell, an explosion they started),
     * judged by the island at the position as an onBreakBlock without a target: no block carve-out lets it through, so
     * only a group with the break permission may break blocks there.
     */
    public static boolean isBreakDenied(final Player player, final ServerLevel level, final BlockPos position) {
        return player instanceof final ServerPlayer serverPlayer && breakDenialPerIsland(serverPlayer, level).test(position);
    }

    /**
     * {@link #isBreakDenied} for many positions at once (an explosion): the answer is the same everywhere on one island,
     * so each island is looked up and judged once.
     */
    public static Predicate<BlockPos> breakDenialPerIsland(final ServerPlayer player, final ServerLevel level) {
        if (player instanceof FakePlayer || level.dimension() != Level.OVERWORLD || player.hasPermissions(Commands.LEVEL_ADMINS)) return position -> false;

        final Map<Island, Boolean> denialByIsland = new HashMap<>();
        return position -> {
            final yorickbm.skyblockaddon.core.util.geometry.Vec3i islandPosition = ForgeConverter.forgeToInternalVec3i(position);
            final Island island = denialByIsland.keySet().stream()
                    .filter(judged -> judged.getIslandBoundingBox().isInside(islandPosition))
                    .findFirst()
                    .orElseGet(() -> IslandManager.getInstance().getIslandByPos(islandPosition));
            if (island == null) return false;
            return denialByIsland.computeIfAbsent(island, guarding -> !guarding.isOwner(player.getUUID())
                    && isInteractionDenied(new AtomicReference<>(guarding), player, PermissionTriggers.ON_BREAK_BLOCK, subjectOfUntargetedBreak(player.getMainHandItem())));
        };
    }

    /**
     * Whether using the hand item would put an entity into the world that the player may not place here. Judged on the
     * item alone, so the clicked block and its carve-outs never let it through.
     */
    public static boolean isEntityPlacementDenied(final AtomicReference<Island> standingOn, final ServerPlayer player, final ItemStack handItem) {
        if (!EntityPlacerItems.isEntityPlacer(handItem)) return false;

        final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(player.getUUID());
        if (group.isEmpty()) return true;

        final Optional<Permission> denyingPermission = InteractionPermissionDecision.findDenyingEntityPlacement(
                PermissionManager.getInstance().getPermissionsForTrigger(PermissionTriggers.ON_PLACE_BLOCK), group.get()::canDo, itemId(handItem).orElseThrow());
        denyingPermission.ifPresent(permission -> SkyBlockAddon.logPermissionDebug(LOGGER, handItem.getItem().getRegistryName() + " (entity placer) is BLOCKED on " + permission.getId() + " in group " + group.get().getName()));
        return denyingPermission.isPresent();
    }

    /**
     * Let the clicked block react but never run the hand item, and undo the client's predicted use of the stack. The
     * denial is announced only when the item is what the click would use; opening an allowed chest with a boat in hand
     * stays silent.
     */
    public static void denyItemUseOnBlock(final PlayerInteractEvent.RightClickBlock event, final ServerPlayer player, final boolean announceDenial) {
        event.setUseItem(Event.Result.DENY);
        if (announceDenial) sendDeniedMessage(player);
        player.inventoryMenu.sendAllDataToRemote();
    }

    /**
     * A click on a block: the hand item, the block it would place and the clicked block (none when air).
     */
    public static InteractionSubject subjectOfClick(final ServerLevel world, final BlockPos position, final ItemStack handItem) {
        return subjectOfClickOn(world.getBlockState(position), handItem);
    }

    /**
     * A click on a block that is not in the world at the clicked position (a block of a moving contraption).
     */
    public static InteractionSubject subjectOfClickOn(final BlockState clickedState, final ItemStack handItem) {
        final Optional<String> clickedBlockId = clickedState.isAir() ? Optional.empty() : Optional.of(blockId(clickedState.getBlock()));
        return new InteractionSubject(itemId(handItem), heldBlockId(handItem), clickedBlockId);
    }

    /**
     * A block that was placed, whatever placed it. Wall variants are judged as the block their item places (a wall
     * player head as {@code minecraft:player_head}) so carve-outs written for the held block still apply.
     */
    public static InteractionSubject subjectOfPlacedBlock(final ItemStack handItem, final BlockState placedState) {
        final Block placedBlock = placedState.getBlock();
        final Block judgedBlock = placedBlock.asItem() instanceof final BlockItem placingItem ? placingItem.getBlock() : placedBlock;
        return new InteractionSubject(itemId(handItem), Optional.of(blockId(judgedBlock)), Optional.of(blockId(judgedBlock)));
    }

    /**
     * A placement whose target is unknown (gadgets, reacharound): judged on the placing stack alone, so without a
     * held block any lacked permission with block rules denies it.
     */
    public static InteractionSubject subjectOfUntargetedPlacement(final ItemStack placingStack) {
        return new InteractionSubject(itemId(placingStack), heldBlockId(placingStack), Optional.empty());
    }

    /**
     * A break by an area tool (gadgets): judged on the tool alone, so without a target block any lacked permission with
     * block rules denies it, wherever the player stands.
     */
    public static InteractionSubject subjectOfUntargetedBreak(final ItemStack breakingTool) {
        return new InteractionSubject(itemId(breakingTool), Optional.empty(), Optional.empty());
    }

    /**
     * The stack a hand-agnostic placement uses: the main hand when it holds a block or the off hand does not, else the
     * off hand.
     */
    public static ItemStack placingStack(final Player player) {
        final ItemStack mainHandItem = player.getMainHandItem();
        final ItemStack offhandItem = player.getOffhandItem();
        return mainHandItem.getItem() instanceof BlockItem || !(offhandItem.getItem() instanceof BlockItem) ? mainHandItem : offhandItem;
    }

    private static Optional<String> itemId(final ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return Optional.of(Objects.requireNonNull(stack.getItem().getRegistryName()).toString());
    }

    private static Optional<String> heldBlockId(final ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof final BlockItem blockItem)) return Optional.empty();
        return Optional.of(blockId(blockItem.getBlock()));
    }

    private static String blockId(final Block block) {
        return Objects.requireNonNull(block.getRegistryName()).toString();
    }
}
