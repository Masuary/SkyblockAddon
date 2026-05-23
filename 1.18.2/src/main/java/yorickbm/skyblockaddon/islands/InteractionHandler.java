package yorickbm.skyblockaddon.islands;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.util.FakePlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.SkyBlockAddon;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.EntityVerification;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.util.MatchResult;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class InteractionHandler {
    private static final Logger LOGGER = LogManager.getLogger();

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
     * Run player interaction handler to determine if event is to be canceld or not.
     *
     * @param standingOn - Island player is standing on
     * @param trigger - Trigger type to use
     */
    public static boolean checkPlayerInteraction(final AtomicReference<Island> standingOn, final ServerPlayer player, final ServerLevel world, final BlockPos position, final ItemStack handItem, final String trigger) {
        SkyBlockAddon.CustomDebugMessages(LOGGER, trigger);

        //Update doors
        if (player.getLevel().getBlockState(position).getBlock() instanceof DoorBlock) {
            final DoubleBlockHalf half = (player).getLevel().getBlockState(position).getValue(DoorBlock.HALF);
            if (half == DoubleBlockHalf.LOWER) {
                final BlockState other = (player).getLevel().getBlockState(position.above());
                ServerHelper.SendPacket(player, new ClientboundBlockUpdatePacket(position.above(), other));
            } else {
                final BlockState other = (player).getLevel().getBlockState(position.below());
                ServerHelper.SendPacket(player, new ClientboundBlockUpdatePacket(position.below(), other));
            }
        }

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

            if(!clickedState.isAir() && !blocksData.isEmpty()) {
                final Block clickedBlock = clickedState.getBlock();
                final String block = Objects.requireNonNull(clickedBlock.getRegistryName()).toString();

                final MatchResult rslt = PermissionManager.checkMatch(blocksData, block);
                SkyBlockAddon.CustomDebugMessages(LOGGER, "b) " + block + " is " + rslt + " on " + perm.getId() + " in group " + group.get().getName());
                switch(rslt) {
                    case SKIP, ALLOW-> { }
                    case BLOCK ->  blockAllowed = false;
                }
            }

            runFail = (!blockAllowed || !itemAllowed);
        }

        //Update redstone signals to cancel
        if(runFail) {
            ServerHelper.forceUnpowerOrTogglePoweredBlock(world, position);
        }

        return runFail;
    }
}
