package yorickbm.skyblockaddon.islands;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.SkyBlockAddon;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.core.islands.InteractionValidator;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.EntityVerification;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;



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

        final net.minecraft.server.MinecraftServer netherServer = player.getServer();
        if (netherServer == null) return EntityVerification.CAP_NOT_FOUND;
        final ServerLevel overworldLevel = netherServer.getLevel(Level.OVERWORLD);
        if (overworldLevel == null) return EntityVerification.CAP_NOT_FOUND;
        final Optional<SkyblockAddonWorldCapability> cap = overworldLevel.getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).resolve();
        if(cap.isEmpty()) return EntityVerification.CAP_NOT_FOUND;


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
        if (group.isEmpty()) return true;

        final String itemName = handItem.isEmpty() ? "" : Objects.requireNonNull(handItem.getItem().getRegistryName()).toString();
        final BlockState clickedState = world.getBlockState(position);
        final String blockName;
        if ("onPlaceBlock".equals(trigger) && handItem.getItem() instanceof BlockItem blockItem) {
            blockName = Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(blockItem.getBlock())).toString();
        } else {
            blockName = clickedState.isAir()
                    ? ""
                    : Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(clickedState.getBlock())).toString();
        }

        final Map<String, String> ctx = new LinkedHashMap<>();
        if (!blockName.isEmpty()) ctx.put("block", blockName);
        if (!itemName.isEmpty()) ctx.put("item", itemName);

        return evaluatePermissions(group.get(), trigger, ctx);
    }

    /**
     * Entity right-click interaction check — passes entity type as context so
     * permissions with entity filters (e.g. open_chests for chest_minecart) work correctly.
     */
    public static boolean checkEntityInteraction(final AtomicReference<Island> standingOn,
                                                 final ServerPlayer player,
                                                 final Entity target,
                                                 final ItemStack handItem,
                                                 final String trigger) {
        SkyBlockAddon.CustomDebugMessages(LOGGER, trigger);

        final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(player.getUUID());
        if (group.isEmpty()) return true;

        final String itemName = handItem.isEmpty() ? "" : Objects.requireNonNull(handItem.getItem().getRegistryName()).toString();
        final var entityKey = ForgeRegistries.ENTITIES.getKey(target.getType());
        final String entityName = entityKey != null ? entityKey.toString() : "";

        final Map<String, String> ctx = new LinkedHashMap<>();
        if (!itemName.isEmpty()) ctx.put("item", itemName);
        if (!entityName.isEmpty()) ctx.put("entity", entityName);

        return evaluatePermissions(group.get(), trigger, ctx);
    }

    /** Shared: run permission check with optional debug logging. Returns true if blocked. */
    private static boolean evaluatePermissions(final IslandGroup group,
                                               final String trigger,
                                               final Map<String, String> ctx) {
        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger(trigger);
        final boolean debugEnabled = SkyBlockAddon.isDebugEnabled();
        final InteractionValidator.PermissionDebug debug = debugEnabled ? new InteractionValidator.PermissionDebug() : null;

        final boolean blocked = InteractionValidator.checkPermission(group, perms, ctx, debug);

        if (debugEnabled) {
            final String header = "[PermCheck] trigger=" + trigger + " "
                    + ctx.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(" "))
                    + " group=" + group.getName() + " -> " + (blocked ? "BLOCKED" : "ALLOWED");
            debug.log(LOGGER::info, header);
        }

        return blocked;
    }
}
