package yorickbm.skyblockaddon.events;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.InteractionValidator;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.islands.InteractionHandler;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class PermissionEvents {

    // ── Permission events ──────────────────────────────────────────────────

    @SubscribeEvent
    public void onTrample(final BlockEvent.FarmlandTrampleEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onTrample", "entity", Objects.requireNonNull(EntityType.getKey(event.getEntity().getType())).toString()),
                event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onEnderPearl(final EntityTeleportEvent.EnderPearl event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onEnderPearl", "item", "minecraft:ender_pearl"), event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onChorusFruit(final EntityTeleportEvent.ChorusFruit event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onChorusFruit", "item", "minecraft:chorus_fruit"), event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onSleepInBed(final PlayerSleepInBedEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        final String bed = Objects.requireNonNull(
                event.getEntity().getLevel().getBlockState(event.getPos()).getBlock().getRegistryName()).toString();
        denyIf(check(group, "onSleepInBed", "block", bed), event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onXp(final PlayerXpEvent.PickupXp event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onXp", "entity", Objects.requireNonNull(EntityType.getKey(event.getOrb().getType())).toString()),
                event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onBonemeal(final BonemealEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onBonemeal", "block", Objects.requireNonNull(event.getBlock().getBlock().getRegistryName()).toString()),
                event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onPickup(final EntityItemPickupEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onPickup", "item", Objects.requireNonNull(event.getItem().getItem().getItem().getRegistryName()).toString()),
                event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onDrop(final ItemTossEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getPlayer(), event);
        if (group.isEmpty()) return;
        final boolean blocked = check(group, "onDrop", "item",
                Objects.requireNonNull(event.getEntityItem().getItem().getItem().getRegistryName()).toString());
        if (blocked) {
            denyEvent(event, (ServerPlayer) event.getPlayer());
            event.getPlayer().addItem(event.getEntityItem().getItem());
            event.getPlayer().getInventory().setChanged();
        }
    }

    @SubscribeEvent
    public void onBucket(final FillBucketEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        final ItemStack bucket = event.getFilledBucket() == null ? event.getEmptyBucket() : event.getFilledBucket();
        denyIf(check(group, "onBucket", "item", Objects.requireNonNull(bucket.getItem().getRegistryName()).toString()),
                event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onMount(final EntityMountEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onMount", "entity", Objects.requireNonNull(EntityType.getKey(event.getEntityBeingMounted().getType())).toString()),
                event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onAttack(final AttackEntityEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getPlayer(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onAttack", "entity", Objects.requireNonNull(EntityType.getKey(event.getTarget().getType())).toString()),
                event, (ServerPlayer) event.getPlayer());
    }

    @SubscribeEvent
    public void onUse(final LivingEntityUseItemEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        denyIf(check(group, "onUse", "item", Objects.requireNonNull(event.getItem().getItem().getRegistryName()).toString()),
                event, (ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(final EntityTravelToDimensionEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;
        final boolean blocked = check(group, "onPlayerChangedDimension", "dimension",
                event.getDimension().location().toString());
        if (blocked) {
            denyEvent(event, (ServerPlayer) event.getEntity());
            final ServerPlayer player = (ServerPlayer) event.getEntity();
            final double radianYaw = Math.toRadians(player.getYRot());
            final Vec3 direction = new Vec3(Math.sin(radianYaw), 0, -Math.cos(radianYaw));
            player.setDeltaMovement(new Vec3(direction.x * 0.4, 0.28, direction.z * 0.4));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    // ── Block/item interaction events ──────────────────────────────────────

    @SubscribeEvent
    public void onClickEntity(final PlayerInteractEvent.EntityInteract event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            if (InteractionHandler.checkEntityInteraction(standingOn, (ServerPlayer) event.getPlayer(),
                    event.getTarget(), event.getItemStack(), "onRightClickEntity")) {
                denyEvent(event, (ServerPlayer) event.getPlayer());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            final String trigger = ServerHelper.isBlockInteractable(event.getWorld(), event.getPos(),
                    event.getPlayer(), event.getHand(), event.getHitVec()) ? "onRightClickBlock" : "onPlaceBlock";
            if (handleBlockEvent(standingOn, event, trigger)) {
                resyncDoubleBlock(event);
            }
        }
        checkNetherPortalIgnition(event.getEntity(), standingOn, event.getPos(), event.getItemStack(), event);
    }

    @SubscribeEvent
    public void onRightClickEmpty(final PlayerInteractEvent.RightClickEmpty event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            handleBlockEvent(standingOn, event, "onRightClickEmpty");
        }
    }

    @SubscribeEvent
    public void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            handleBlockEvent(standingOn, event, "onRightClickItem");
        }
    }

    @SubscribeEvent
    public void onEntityPlace(final BlockEvent.EntityPlaceEvent event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            if (InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getEntity(),
                    (ServerLevel) event.getWorld(), event.getPos(),
                    ((ServerPlayer) event.getEntity()).getMainHandItem(), "onPlaceBlock")) {
                denyEvent(event, (ServerPlayer) event.getEntity());
            }
        }
        checkNetherPortalIgnition(event.getEntity(), standingOn, event.getPos(),
                ((ServerPlayer) Objects.requireNonNull(event.getEntity())).getMainHandItem(), event);
    }

    @SubscribeEvent
    public void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            handleBlockEvent(standingOn, event, "onLeftClickBlock");
        }
    }

    @SubscribeEvent
    public void onLeftClickEmpty(final PlayerInteractEvent.LeftClickEmpty event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            handleBlockEvent(standingOn, event, "onLeftClickEmpty");
        }
    }

    @SubscribeEvent
    public void onMobSpawn(final LivingSpawnEvent.CheckSpawn event) {
        final Optional<SkyblockAddonWorldCapability> cap = event.getEntity().getLevel()
                .getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).resolve();
        if (cap.isEmpty()) return;
        final Island island = IslandManager.getInstance().getIslandByPos(
                ForgeConverter.ForgeToInternalVec3i(event.getEntity().getOnPos()));
        if (island == null) return;
        final String trigger = event.getEntity().getType().getCategory() == MobCategory.MONSTER
                ? "OnHostileMobSpawn" : "OnPassiveMobSpawn";
        final boolean blocked = InteractionValidator.checkPermission(
                island.getGroupForEntityUUID(event.getEntity().getUUID()).orElse(null),
                PermissionManager.getInstance().getPermissionsForTrigger(trigger),
                Map.of("entity", event.getEntity().getType().toString()));
        if (blocked) {
            event.setResult(Event.Result.DENY);
            if (event.isCancelable()) event.setCanceled(true);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean check(final Optional<IslandGroup> group, final String trigger,
                          final String context, final String value) {
        return InteractionValidator.checkPermission(
                group.orElseThrow(),
                PermissionManager.getInstance().getPermissionsForTrigger(trigger),
                Map.of(context, value));
    }

    private Optional<IslandGroup> verifyPermissionAndGroup(final Entity entity, final Event event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(entity, standingOn).asBoolean()) return Optional.empty();
        final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(entity.getUUID());
        if (group.isEmpty()) denyEvent(event, (ServerPlayer) entity);
        return group;
    }

    /** Runs checkPlayerInteraction for a PlayerInteractEvent and denies if blocked. Returns true if blocked. */
    private boolean handleBlockEvent(final AtomicReference<Island> standingOn,
                                     final PlayerInteractEvent event,
                                     final String trigger) {
        if (InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getPlayer(),
                (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(), trigger)) {
            denyEvent(event, (ServerPlayer) event.getPlayer());
            return true;
        }
        return false;
    }

    private void denyIf(final boolean blocked, final Event event, final ServerPlayer player) {
        if (blocked) denyEvent(event, player);
    }

    private void denyEvent(Event event, ServerPlayer player) {
        if (event instanceof PlayerInteractEvent.RightClickBlock) {
            PlayerInteractEvent.RightClickBlock rcb = (PlayerInteractEvent.RightClickBlock) event;
            rcb.setUseBlock(Event.Result.DENY);
            rcb.setUseItem(Event.Result.DENY);
            player.connection.send(new ClientboundBlockUpdatePacket(rcb.getWorld(), rcb.getPos()));

            BlockEntity be = rcb.getWorld().getBlockEntity(rcb.getPos());
            if (be != null) {
                Packet<?> packet = be.getUpdatePacket();
                if (packet != null) {
                    player.connection.send(packet);
                }
            }

            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }

        } else if (event.isCancelable()) {
            event.setCanceled(true);
        } else {
            event.setResult(Event.Result.DENY);
        }

        player.displayClientMessage(
                new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                        .withStyle(ChatFormatting.DARK_RED),
                true);
    }

    /**
     * Doors are two-block-tall structures. Forge's ack flow resolves the client prediction for
     * the clicked half. We only need to explicitly resync the OTHER half, which Minecraft's
     * vanilla ack path does not cover.
     */
    private void resyncDoubleBlock(final PlayerInteractEvent.RightClickBlock event) {
        final var blockState = event.getWorld().getBlockState(event.getPos());
        if (!(blockState.getBlock() instanceof DoorBlock)) return;

        final ServerPlayer player = (ServerPlayer) event.getPlayer();
        final BlockPos clicked = event.getPos();
        final BlockPos otherHalf = blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? clicked.below()
                : clicked.above();

        player.connection.send(new ClientboundBlockUpdatePacket(event.getWorld(), otherHalf));
    }

    private void checkNetherPortalIgnition(final Entity entity, final AtomicReference<Island> standingOn,
                                           final net.minecraft.core.BlockPos pos, final ItemStack item,
                                           final Event event) {
        if (!InteractionHandler.verifyNetherEntity(entity, standingOn, pos).asBoolean()) {
            if (!item.isEmpty() && item.getItem() == Items.FLINT_AND_STEEL) {
                final var state = Objects.requireNonNull(entity.getLevel()).getBlockState(pos);
                if (!state.isAir() && state.getBlock().asItem() == Items.OBSIDIAN) {
                    if (InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) entity,
                            (ServerLevel) entity.getLevel(), pos, item, "onPortalIgnition")) {
                        denyEvent(event, (ServerPlayer) entity);
                    }
                }
            }
        }
    }
}
