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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
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
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.core.util.MatchResult;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class InteractionHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void deny(final Event event, final Player player) {
        if (event.isCancelable()) event.setCanceled(true);
        else event.setResult(Event.Result.DENY);
        sendDeniedMessage(player);
    }

    public static void sendDeniedMessage(final Player player) {
        player.displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
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
        if(logDecision) SkyBlockAddon.CustomDebugMessages(LOGGER, trigger);

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger(trigger);
        if (perms.isEmpty()) {
            if(logDecision) SkyBlockAddon.CustomDebugMessages(LOGGER, "No permissions found for trigger (ALLOWED)");
            return false;
        }
        if(group.isEmpty()) return false;

        boolean runFail = false;
        for (final Permission perm : perms) {
            if (group.get().canDo(perm.getId())) {
                if(logDecision) SkyBlockAddon.CustomDebugMessages(LOGGER, "action is ALLOWED on " + perm.getId() + " in group " + group.get().getName());
                continue;
            }
            if (runFail) break;

            final List<String> data = dataExtractor.apply(perm.getData());
            if (data.isEmpty()) {
                if(logDecision) SkyBlockAddon.CustomDebugMessages(LOGGER, "action is BLOCKED on " + perm.getId() + " in group " + group.get().getName());
                runFail = true;
            } else {
                final MatchResult rslt = PermissionManager.checkMatch(data, matchValue);
                if(logDecision) SkyBlockAddon.CustomDebugMessages(LOGGER, matchValue + " is " + rslt + " on " + perm.getId() + " in group " + group.get().getName());
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

                final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(player.getOnPos()));
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


        final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(new BlockPos(triggerPoint.getX() * 8, triggerPoint.getY(), triggerPoint.getZ() * 8)));
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
        final boolean denied = isInteractionDenied(standingOn, player, world, position, handItem, trigger);
        if (denied) {
            resendBlockToPlayer(player, world, position);
        }
        return denied;
    }

    public static void resendBlockToPlayer(final ServerPlayer player, final ServerLevel world, final BlockPos position) {
        final BlockState clickedState = world.getBlockState(position);
        ServerHelper.SendPacket(player, new ClientboundBlockUpdatePacket(position, clickedState));

        if (clickedState.getBlock() instanceof DoorBlock) {
            final BlockPos otherHalf = clickedState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? position.above() : position.below();
            ServerHelper.SendPacket(player, new ClientboundBlockUpdatePacket(otherHalf, world.getBlockState(otherHalf)));
        }

        final BlockEntity blockEntity = world.getBlockEntity(position);
        if (blockEntity != null) {
            final Packet<ClientGamePacketListener> blockEntityUpdate = blockEntity.getUpdatePacket();
            if (blockEntityUpdate != null) ServerHelper.SendPacket(player, blockEntityUpdate);
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
        SkyBlockAddon.CustomDebugMessages(LOGGER, trigger);

        final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(player.getUUID());
        if(group.isEmpty()) {
            return true;
        }

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger(trigger);
        if(perms.isEmpty()) return false; //No permission to protect against it

        boolean runFail = false;
        for(final Permission perm : perms) {
            if(group.get().canDo(perm.getId())) {
                //SkyblockAddon.CustomDebugMessages(LOGGER, "action is allowed for " + perm.getId() + " in group " + group.get().getItem().getDisplayName().getString().trim() + " on " + trigger);
                continue;
            }
            if(runFail) break; //Break loop if we determine failure

            // Get permission item data and check for empty
            final List<String> itemsData = perm.getData().getItemsData();
            final List<String> blocksData = perm.getData().getBlocksData();
            final BlockState clickedState = world.getBlockState(position);

            // Determine default
            if(itemsData.isEmpty() && blocksData.isEmpty()) {
                runFail = true;
                break;
            }

            boolean itemAllowed = true;
            boolean blockAllowed = true;

            if(!handItem.isEmpty() && !itemsData.isEmpty()) {
                final String item = Objects.requireNonNull(handItem.getItem().getRegistryName()).toString();

                final MatchResult rslt = PermissionManager.checkMatch(itemsData, item);
                SkyBlockAddon.CustomDebugMessages(LOGGER, "i) " + item + " is " + rslt + " on " + perm.getId() + " in group " + group.get().getName());
                switch(rslt) {
                    case SKIP, ALLOW -> { }
                    case BLOCK ->  itemAllowed = false;
                }
            }

            if(!blocksData.isEmpty()) {
                final Block subjectBlock;
                if(PermissionTriggers.ON_PLACE_BLOCK.equals(trigger)) {
                    subjectBlock = (handItem.getItem() instanceof BlockItem heldBlockItem) ? heldBlockItem.getBlock() : null;
                } else if(!clickedState.isAir()) {
                    subjectBlock = clickedState.getBlock();
                } else {
                    subjectBlock = null;
                }

                if(subjectBlock != null) {
                    final String block = Objects.requireNonNull(subjectBlock.getRegistryName()).toString();
                    final MatchResult rslt = PermissionManager.checkMatch(blocksData, block);
                    SkyBlockAddon.CustomDebugMessages(LOGGER, "b) " + block + " is " + rslt + " on " + perm.getId() + " in group " + group.get().getName());
                    switch(rslt) {
                        case SKIP, ALLOW -> { }
                        case BLOCK -> blockAllowed = false;
                    }
                }
            }

            runFail = (!blockAllowed || !itemAllowed);
        }

        return runFail;
    }
}
