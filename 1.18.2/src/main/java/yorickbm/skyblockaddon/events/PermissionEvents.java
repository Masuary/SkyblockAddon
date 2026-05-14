package yorickbm.skyblockaddon.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.util.MatchResult;
import yorickbm.skyblockaddon.islands.InteractionHandler;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PermissionEvents {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public void onTrample(final BlockEvent.FarmlandTrampleEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger("onTrample");
        if(perms.isEmpty()) {
            return; //No permission to protect against it
        }

        final boolean runFail = !group.get().canDo(perms.get(0).getId());
        if (runFail) { //Group may not run this
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }
    @SubscribeEvent
    public void onEnderPearl(final EntityTeleportEvent.EnderPearl event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger("onEnderPearl");
        if(perms.isEmpty()) {
            return; //No permission to protect against it
        }

        final boolean runFail = !group.get().canDo(perms.get(0).getId());
        if (runFail) { //Group may not run this
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }
    @SubscribeEvent
    public void onChorusFruit(final EntityTeleportEvent.ChorusFruit event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger("onChorusFruit");
        if(perms.isEmpty()) {
            return; //No permission to protect against it
        }

        final boolean runFail = !group.get().canDo(perms.get(0).getId());
        if (runFail) { //Group may not run this
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onSleepInBed(final PlayerSleepInBedEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger("onSleepInBed");
        if(perms.isEmpty()) {
            return; //No permission to protect against it
        }

        final boolean runFail = !group.get().canDo(perms.get(0).getId());
        if (runFail) { //Group may not run this
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }
    @SubscribeEvent
    public void onXp(final PlayerXpEvent.PickupXp event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger("onXp");
        if(perms.isEmpty()) {
            return; //No permission to protect against it
        }

        final boolean runFail = !group.get().canDo(perms.get(0).getId());
        if (runFail) { //Group may not run this
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }
    @SubscribeEvent
    public void onBonemeal(final BonemealEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger("onBonemeal");
        if(perms.isEmpty()) {
            return; //No permission to protect against it
        }

        final boolean runFail = !group.get().canDo(perms.get(0).getId());
        if (runFail) { //Group may not run this
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onPickup(final EntityItemPickupEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final boolean runFail = processPermissions(
                group, "onPickup",
                Objects.requireNonNull(event.getItem().getItem().getItem().getRegistryName()).toString(),
                PermissionDataJson::getItemsData
        );

        if (runFail) {
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onDrop(final ItemTossEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getPlayer(), event);
        if (group.isEmpty()) return;

        final boolean runFail = processPermissions(
                group, "onDrop",
                Objects.requireNonNull(event.getEntityItem().getItem().getItem().getRegistryName()).toString(),
                PermissionDataJson::getItemsData
        );

        if (runFail) {
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getPlayer());

            event.getPlayer().addItem(event.getEntityItem().getItem());
            event.getPlayer().getInventory().setChanged();
        }
    }

    @SubscribeEvent
    public void onBucket(final FillBucketEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        ItemStack bucket = event.getFilledBucket() == null ? event.getEmptyBucket() : event.getFilledBucket();

        final boolean runFail = processPermissions(
                group, "onBucket",
                Objects.requireNonNull(bucket.getItem().getRegistryName()).toString(),
                PermissionDataJson::getItemsData
        );

        if (runFail) {
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onMount(final EntityMountEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final boolean runFail = processPermissions(
                group, "onMount",
                Objects.requireNonNull(EntityType.getKey(event.getEntityBeingMounted().getType())).toString(),
                PermissionDataJson::getEntitiesData
        );

        if (runFail) {
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onClickEntity(PlayerInteractEvent.EntityInteract event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getPlayer(), (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(),  "onRightClickEntity")) {
                event.getPlayer().displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
                if(event.isCancelable()) event.setCanceled(true);
                else event.setResult(Event.Result.DENY);
            }
        }

    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            final BlockState state = event.getWorld().getBlockState(event.getPos());
            final BlockEntity be = event.getWorld().getBlockEntity(event.getPos());
            final String trigger = ServerHelper.isBlockInteractable(event.getWorld(), event.getPos(), event.getPlayer(), event.getHand(), event.getHitVec()) ? "onRightClickBlock" : "onPlaceBlock";

            if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getPlayer(), (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(),  trigger)) {
                event.getPlayer().displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
                if(event.isCancelable()) event.setCanceled(true);
                else event.setResult(Event.Result.DENY);
            }
        }

        //Nether portal ignition check from nether to over-world
        if(!InteractionHandler.verifyNetherEntity(event.getEntity(), standingOn, event.getPos()).asBoolean()) {
            final ItemStack item = event.getItemStack();
            if(!item.isEmpty() && item.getItem() == Items.FLINT_AND_STEEL) {
                final BlockState state = event.getWorld().getBlockState(event.getPos());
                if(!state.isAir() && state.getBlock().asItem() == Items.OBSIDIAN) {
                    if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getPlayer(), (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(), "onPortalIgnition")) {
                        event.getPlayer().displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
                        if(event.isCancelable()) event.setCanceled(true);
                        else event.setResult(Event.Result.DENY);
                    }
                }
            }
        }
    }
    @SubscribeEvent
    public void onRightClickEmpty(final PlayerInteractEvent.RightClickEmpty event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) return;

        if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getPlayer(), (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(), "onRightClickEmpty")) {
            event.getPlayer().displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
            if(event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
        }
    }
    @SubscribeEvent
    public void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) return;

        if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getPlayer(), (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(), "onRightClickItem")) {
            event.getPlayer().displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
            if(event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
        }
    }
    @SubscribeEvent
    public void onRightClickEmpty(final BlockEvent.EntityPlaceEvent event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            final BlockState state = event.getWorld().getBlockState(event.getPos());
            final BlockEntity be = event.getWorld().getBlockEntity(event.getPos());
            final String trigger = "onPlaceBlock";

            if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getEntity(), (ServerLevel) event.getWorld(), event.getPos(), ((ServerPlayer) event.getEntity()).getMainHandItem(),  trigger)) {
                ((ServerPlayer) event.getEntity()).displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
                if(event.isCancelable()) event.setCanceled(true);
                else event.setResult(Event.Result.DENY);
            }
        }

        //Nether portal ignition check from nether to over-world
        if(!InteractionHandler.verifyNetherEntity(event.getEntity(), standingOn, event.getPos()).asBoolean()) {
            final ItemStack item = ((ServerPlayer) Objects.requireNonNull(event.getEntity())).getMainHandItem();
            if(!item.isEmpty() && item.getItem() == Items.FLINT_AND_STEEL) {
                final BlockState state = event.getWorld().getBlockState(event.getPos());
                if(!state.isAir() && state.getBlock().asItem() == Items.OBSIDIAN) {
                    if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getEntity(), (ServerLevel) event.getWorld(), event.getPos(), ((ServerPlayer) event.getEntity()).getMainHandItem(), "onPortalIgnition")) {
                        ((ServerPlayer) event.getEntity()).displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
                        if(event.isCancelable()) event.setCanceled(true);
                        else event.setResult(Event.Result.DENY);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) return;

        if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getPlayer(), (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(), "onLeftClickBlock")) {
            event.getPlayer().displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
            if(event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
        }
    }
    @SubscribeEvent
    public void onLeftClickEmpty(final PlayerInteractEvent.LeftClickEmpty event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) return;

        if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) event.getPlayer(), (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(), "onLeftClickEmpty")) {
            event.getPlayer().displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
            if(event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onAttack(final AttackEntityEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getPlayer(), event);
        if (group.isEmpty()) return;

        final boolean runFail = processPermissions(
                group,
                "onAttack",
                Objects.requireNonNull(EntityType.getKey(event.getTarget().getType())).toString(),
                PermissionDataJson::getEntitiesData
        );

        if (runFail) {
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onUse(final LivingEntityUseItemEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final boolean runFail = processPermissions(
                group,
                "onUse",
                Objects.requireNonNull(event.getItem().getItem().getRegistryName()).toString(),
                PermissionDataJson::getItemsData
        );

        if (runFail) {
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(final EntityTravelToDimensionEvent event) {
        final Optional<IslandGroup> group = verifyPermissionAndGroup(event.getEntity(), event);
        if (group.isEmpty()) return;

        final boolean runFail = processPermissions(
                group,
                "onPlayerChangedDimension",
                event.getDimension().location().toString(),
                data -> data.getSkyblockaddonData().stream()
                        .filter(s -> s.startsWith("dimension:"))
                        .map(s -> s.replace("dimension:", ""))
                        .collect(Collectors.toList())
        );

        if (runFail) {
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) event.getEntity());

            final ServerPlayer player = (ServerPlayer) event.getEntity();
            final double radianYaw = Math.toRadians(player.getYRot());
            final Vec3 direction = new Vec3(Math.sin(radianYaw), 0, -Math.cos(radianYaw));
            player.setDeltaMovement(new Vec3(direction.x * 0.4, 0.28, direction.z * 0.4));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    private Optional<IslandGroup> verifyPermissionAndGroup(final Entity entity, final Event event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(entity, standingOn).asBoolean()) {
            return Optional.empty();
        }

        final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(entity.getUUID());
        if (group.isEmpty()) {
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            sendFailureMessage((ServerPlayer) entity);
            return Optional.empty();
        }

        return group;
    }

    @SubscribeEvent
    public void onMobSpawn(LivingSpawnEvent.CheckSpawn event) {
        EntityType<?> entityType = event.getEntity().getType();
        final Optional<SkyblockAddonWorldCapability> cap = event.getEntity().getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).resolve();
        if(cap.isEmpty()) return;

        final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(event.getEntity().getOnPos()));
        if(island == null) return;

        final boolean runFail = processPermissions(
                island.getGroupForEntityUUID(event.getEntity().getUUID()),
                (entityType.getCategory() == MobCategory.MONSTER ? "OnHostileMobSpawn" : "OnPassiveMobSpawn"),
                event.getEntity().getType().toString(),
                PermissionDataJson::getEntitiesData,
                false
        );

        if(runFail) {
            event.setResult(Event.Result.DENY);
            if(event.isCancelable()) event.setCanceled(true);
        }
    }

    private boolean processPermissions(final Optional<IslandGroup> group, final String trigger, final String matchValue, final Function<PermissionDataJson, List<String>> dataExtractor) {
        return this.processPermissions(group, trigger, matchValue, dataExtractor, true);
    }
        private boolean processPermissions(final Optional<IslandGroup> group, final String trigger, final String matchValue, final Function<PermissionDataJson, List<String>> dataExtractor, boolean log) {
        if(log) SkyBlockAddon.CustomDebugMessages(LOGGER, trigger);

        final List<Permission> perms = PermissionManager.getInstance().getPermissionsForTrigger(trigger);
        if (perms.isEmpty()) {
            if(log) SkyBlockAddon.CustomDebugMessages(LOGGER, "No permissions found for trigger (ALLOWED)");
            return false; // No permission to protect against
        }
        if(group.isEmpty()) return false; //No group present should have failed before

        boolean runFail = false;
        for (final Permission perm : perms) {
            if (group.get().canDo(perm.getId())) {
                if(log) SkyBlockAddon.CustomDebugMessages(LOGGER, "action is ALLOWED on " + perm.getId() + " in group " + group.get().getName() + "");
                continue;
            }
            if (runFail) break;

            final List<String> data = dataExtractor.apply(perm.getData());
            if (data.isEmpty()) {
                if(log) SkyBlockAddon.CustomDebugMessages(LOGGER, "action is BLOCKED on " + perm.getId() + " in group " + group.get().getName() + "");
                runFail = true;
            } else {
                final MatchResult rslt = PermissionManager.checkMatch(data, matchValue);
                if(log) SkyBlockAddon.CustomDebugMessages(LOGGER, matchValue + " is " + rslt + " on " + perm.getId() + " in group " + group.get().getName());
                switch (rslt) {
                    case SKIP, ALLOW -> { continue; }
                    case BLOCK -> runFail = true;
                }
            }
        }
        return runFail;
    }

    private void sendFailureMessage(final ServerPlayer player) {
        player.displayClientMessage(
                new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true
        );
    }

}
