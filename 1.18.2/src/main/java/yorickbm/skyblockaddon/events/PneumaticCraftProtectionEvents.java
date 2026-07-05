package yorickbm.skyblockaddon.events;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.SkyBlockAddon;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.lang.reflect.Method;
import java.util.UUID;

public class PneumaticCraftProtectionEvents {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PNEUMATICCRAFT_NAMESPACE = "pneumaticcraft";
    private static final String PNEUMATICCRAFT_PERMISSION = "mod_pneumaticcraft";
    private static final String DRONE_CLASS_NAME = "me.desht.pneumaticcraft.common.entity.drone.DroneEntity";

    private static volatile boolean droneReflectionInitialized;
    private static Class<?> droneClass;
    private static Method droneGetOwnerUuid;

    private static UUID resolveDroneOwner(final Entity entity) {
        initializeDroneReflection();
        if (droneClass == null || entity == null || !droneClass.isInstance(entity)) return null;

        try {
            final UUID owner = (UUID) droneGetOwnerUuid.invoke(entity);
            if (owner == null || owner.equals(entity.getUUID())) {
                // Amadron delivery drones use their own UUID as a no-owner sentinel.
                return null;
            }
            return owner;
        } catch (final ReflectiveOperationException exception) {
            LOGGER.warn("Failed to resolve PneumaticCraft drone owner for {}", entity.getUUID(), exception);
            return null;
        }
    }

    private static void initializeDroneReflection() {
        if (droneReflectionInitialized) return;

        synchronized (PneumaticCraftProtectionEvents.class) {
            if (droneReflectionInitialized) return;
            try {
                droneClass = Class.forName(DRONE_CLASS_NAME);
                droneGetOwnerUuid = droneClass.getMethod("getOwnerUUID");
            } catch (final ReflectiveOperationException exception) {
                LOGGER.warn("PneumaticCraft drone API was not found; owner-aware drone protection is disabled", exception);
                droneClass = null;
                droneGetOwnerUuid = null;
            }
            droneReflectionInitialized = true;
        }
    }

    private static UUID resolveOwnerUuid(final Entity actor) {
        if (actor == null) return null;
        if (actor instanceof ServerPlayer player) return player.getUUID();

        if (actor instanceof Projectile projectile) {
            final Entity projectileOwner = projectile.getOwner();
            if (projectileOwner instanceof ServerPlayer player) return player.getUUID();

            final UUID droneOwner = resolveDroneOwner(projectileOwner);
            if (droneOwner != null) return droneOwner;
        }
        return resolveDroneOwner(actor);
    }

    private static boolean isPneumaticCraftEntity(final Entity entity) {
        if (entity == null) return false;
        final ResourceLocation id = ForgeRegistries.ENTITIES.getKey(entity.getType());
        return id != null && PNEUMATICCRAFT_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean isPneumaticCraftItem(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        final ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && PNEUMATICCRAFT_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean isPneumaticCraftBlock(final BlockState state) {
        if (state == null) return false;
        final ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && PNEUMATICCRAFT_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean shouldBlock(final Entity actor, final BlockPos affectedPos, final LevelAccessor world) {
        if (!(world instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) return false;
        if (actor instanceof ServerPlayer admin && admin.hasPermissions(Commands.LEVEL_ADMINS)) return false;

        final UUID ownerUuid = resolveOwnerUuid(actor);
        return isActionDenied(ownerUuid, affectedPos, world);
    }

    public static boolean isActionDenied(
            final UUID ownerUuid,
            final BlockPos affectedPos,
            final LevelAccessor world
    ) {
        if (!(world instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) return false;
        if (ownerUuid == null) return false;
        final ServerPlayer onlineOwner = serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
        if (onlineOwner != null && onlineOwner.hasPermissions(Commands.LEVEL_ADMINS)) return false;

        final Island island = IslandManager.getInstance()
                .getIslandByPos(ForgeConverter.ForgeToInternalVec3i(affectedPos));
        if (island == null || island.isOwner(ownerUuid)) return false;

        return island.getGroupForEntityUUID(ownerUuid)
                .map(group -> !group.canDo(PNEUMATICCRAFT_PERMISSION))
                .orElse(true);
    }

    private static boolean isPneumaticCraftActor(final Entity entity) {
        return isPneumaticCraftEntity(entity)
                || entity != null && entity.getClass().getName().startsWith("me.desht.pneumaticcraft.");
    }

    private static void notifyDenied(final Entity actor) {
        if (actor instanceof ServerPlayer player) {
            player.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED),
                    true
            );
        }
    }

    private static void debugBlocked(final String message) {
        SkyBlockAddon.CustomDebugMessages(LOGGER, "PneumaticCraft blocked: " + message);
    }

    @SubscribeEvent
    public void onBlockBreak(final BlockEvent.BreakEvent event) {
        final Player player = event.getPlayer();
        if (!isPneumaticCraftItem(player.getMainHandItem()) && !isPneumaticCraftItem(player.getOffhandItem())) return;
        if (!shouldBlock(player, event.getPos(), event.getWorld())) return;

        debugBlocked("BreakEvent at " + event.getPos());
        event.setCanceled(true);
        notifyDenied(player);
    }

    @SubscribeEvent
    public void onEntityPlace(final BlockEvent.EntityPlaceEvent event) {
        final Entity actor = event.getEntity();
        final boolean placedPneumaticCraftBlock = isPneumaticCraftBlock(event.getPlacedBlock());
        final boolean holdingPneumaticCraftItem = actor instanceof Player player
                && (isPneumaticCraftItem(player.getMainHandItem()) || isPneumaticCraftItem(player.getOffhandItem()));
        if (!placedPneumaticCraftBlock && !holdingPneumaticCraftItem) return;
        if (!shouldBlock(actor, event.getPos(), event.getWorld())) return;

        debugBlocked("EntityPlaceEvent at " + event.getPos());
        if (event.isCancelable()) event.setCanceled(true);
        else event.setResult(Event.Result.DENY);
        notifyDenied(actor);
    }

    @SubscribeEvent
    public void onExplosionStart(final ExplosionEvent.Start event) {
        Entity source = event.getExplosion().getSourceMob();
        if (source == null) source = event.getExplosion().getExploder();
        if (!isPneumaticCraftEntity(source)) return;

        final Vec3 center = event.getExplosion().getPosition();
        final BlockPos position = new BlockPos(center.x, center.y, center.z);
        if (!shouldBlock(source, position, event.getWorld())) return;

        debugBlocked("Explosion at " + position);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onProjectileImpact(final ProjectileImpactEvent event) {
        final Projectile projectile = event.getProjectile();
        if (!isPneumaticCraftEntity(projectile)) return;

        final Vec3 hit = event.getRayTraceResult().getLocation();
        final BlockPos position = new BlockPos(hit.x, hit.y, hit.z);
        if (!shouldBlock(projectile, position, projectile.getLevel())) return;

        debugBlocked("ProjectileImpact at " + position);
        event.setCanceled(true);
        projectile.discard();
    }

    @SubscribeEvent
    public void onLivingAttack(final LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) attacker = event.getSource().getDirectEntity();
        if (!isPneumaticCraftActor(attacker)) return;
        if (!shouldBlock(attacker, event.getEntity().blockPosition(), event.getEntity().getLevel())) return;

        debugBlocked("LivingAttack at " + event.getEntity().blockPosition());
        event.setCanceled(true);
        notifyDenied(attacker);
    }

    @SubscribeEvent
    public void onEntityJoinWorld(final EntityJoinWorldEvent event) {
        final Entity entity = event.getEntity();
        if (!isPneumaticCraftEntity(entity)) return;

        final BlockPos position = entity.blockPosition();
        if (!shouldBlock(entity, position, event.getWorld())) return;

        debugBlocked("EntityJoinWorld at " + position);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (!isPneumaticCraftItem(event.getItemStack())) return;
        if (!shouldBlock(event.getPlayer(), event.getPlayer().blockPosition(), event.getWorld())) return;

        debugBlocked("RightClickItem");
        event.setCanceled(true);
        notifyDenied(event.getPlayer());
    }

    @SubscribeEvent
    public void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final boolean pneumaticCraftItem = isPneumaticCraftItem(event.getItemStack());
        final boolean pneumaticCraftBlock = isPneumaticCraftBlock(event.getWorld().getBlockState(event.getPos()));
        if (!pneumaticCraftItem && !pneumaticCraftBlock) return;
        if (!shouldBlock(event.getPlayer(), event.getPos(), event.getWorld())) return;

        debugBlocked("RightClickBlock at " + event.getPos());
        event.setCanceled(true);
        notifyDenied(event.getPlayer());
    }

    @SubscribeEvent
    public void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        if (!isPneumaticCraftItem(event.getItemStack())) return;
        if (!shouldBlock(event.getPlayer(), event.getPos(), event.getWorld())) return;

        debugBlocked("LeftClickBlock at " + event.getPos());
        event.setCanceled(true);
        notifyDenied(event.getPlayer());
    }

    @SubscribeEvent
    public void onUseItemStart(final LivingEntityUseItemEvent.Start event) {
        if (!isPneumaticCraftItem(event.getItem()) || !(event.getEntity() instanceof Player player)) return;
        if (!shouldBlock(player, player.blockPosition(), player.getLevel())) return;

        debugBlocked("UseItemStart");
        event.setCanceled(true);
        notifyDenied(player);
    }
}
