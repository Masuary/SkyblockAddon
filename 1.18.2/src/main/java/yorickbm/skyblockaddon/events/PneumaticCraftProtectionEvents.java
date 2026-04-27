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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
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
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.lang.reflect.Method;
import java.util.UUID;

public class PneumaticCraftProtectionEvents {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PNC_NAMESPACE = "pneumaticcraft";
    private static final String DRONE_CLASS_NAME = "me.desht.pneumaticcraft.common.entity.drone.DroneEntity";

    private static volatile boolean droneReflectionInit = false;
    private static Class<?> droneClass = null;
    private static Method droneGetOwnerUuid = null;

    private static UUID resolveDroneOwner(final Entity entity) {
        if (!droneReflectionInit) {
            synchronized (PneumaticCraftProtectionEvents.class) {
                if (!droneReflectionInit) {
                    try {
                        droneClass = Class.forName(DRONE_CLASS_NAME);
                        droneGetOwnerUuid = droneClass.getMethod("getOwnerUUID");
                    } catch (Throwable ignored) {
                        droneClass = null;
                        droneGetOwnerUuid = null;
                    }
                    droneReflectionInit = true;
                }
            }
        }
        if (droneClass == null || entity == null || !droneClass.isInstance(entity)) return null;
        try {
            final UUID resolvedOwner = (UUID) droneGetOwnerUuid.invoke(entity);
            if (resolvedOwner == null) return null;
            if (resolvedOwner.equals(entity.getUUID())) return null;
            return resolvedOwner;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID resolveOwnerUuid(final Entity actor) {
        if (actor == null) return null;
        if (actor instanceof ServerPlayer player && !(actor instanceof FakePlayer)) return player.getUUID();
        if (actor instanceof Projectile projectile) {
            final Entity projectileOwner = projectile.getOwner();
            if (projectileOwner instanceof ServerPlayer ownerPlayer && !(projectileOwner instanceof FakePlayer)) {
                return ownerPlayer.getUUID();
            }
            final UUID droneOwner = resolveDroneOwner(projectileOwner);
            if (droneOwner != null) return droneOwner;
        }
        return resolveDroneOwner(actor);
    }

    private static boolean isPneumaticCraftEntity(final Entity entity) {
        if (entity == null) return false;
        final ResourceLocation id = ForgeRegistries.ENTITIES.getKey(entity.getType());
        return id != null && PNC_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean isPneumaticCraftItem(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        final ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && PNC_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean isPneumaticCraftBlock(final BlockState state) {
        if (state == null) return false;
        final ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && PNC_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean shouldBlock(final Entity actor, final BlockPos affectedPos, final LevelAccessor world) {
        if (!(world instanceof ServerLevel serverLevel)) return false;
        if (serverLevel.dimension() != Level.OVERWORLD) return false;

        if (actor instanceof ServerPlayer admin && admin.hasPermissions(Commands.LEVEL_ADMINS)) return false;

        final UUID ownerUuid = resolveOwnerUuid(actor);
        if (ownerUuid == null) return false;

        final Island island = IslandManager.getInstance()
                .getIslandByPos(ForgeConverter.ForgeToInternalVec3i(affectedPos));
        if (island == null) return false;

        return !island.isPartOf(ownerUuid);
    }

    private static void notifyDeny(final Entity actor) {
        if (actor instanceof ServerPlayer player) {
            player.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED),
                    true);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(final BlockEvent.BreakEvent event) {
        final Player player = event.getPlayer();
        if (player == null) return;
        if (!isPneumaticCraftItem(player.getMainHandItem()) && !isPneumaticCraftItem(player.getOffhandItem())) return;
        if (shouldBlock(player, event.getPos(), event.getWorld())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: BreakEvent at " + event.getPos());
            event.setCanceled(true);
            notifyDeny(player);
        }
    }

    @SubscribeEvent
    public void onEntityPlace(final BlockEvent.EntityPlaceEvent event) {
        final Entity actor = event.getEntity();
        final boolean placedIsPnc = isPneumaticCraftBlock(event.getPlacedBlock());
        boolean handIsPnc = false;
        if (actor instanceof Player player) {
            handIsPnc = isPneumaticCraftItem(player.getMainHandItem()) || isPneumaticCraftItem(player.getOffhandItem());
        }
        if (!placedIsPnc && !handIsPnc) return;
        if (shouldBlock(actor, event.getPos(), event.getWorld())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: EntityPlaceEvent at " + event.getPos());
            if (event.isCancelable()) event.setCanceled(true);
            else event.setResult(Event.Result.DENY);
            notifyDeny(actor);
        }
    }

    @SubscribeEvent
    public void onExplosionStart(final ExplosionEvent.Start event) {
        Entity source = event.getExplosion().getSourceMob();
        if (source == null) source = event.getExplosion().getExploder();
        if (source == null || !isPneumaticCraftEntity(source)) return;
        final Vec3 center = event.getExplosion().getPosition();
        final BlockPos pos = new BlockPos(center.x, center.y, center.z);
        if (shouldBlock(source, pos, event.getWorld())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: Explosion at " + pos);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onProjectileImpact(final ProjectileImpactEvent event) {
        final Projectile projectile = event.getProjectile();
        if (!isPneumaticCraftEntity(projectile)) return;
        final Vec3 hit = event.getRayTraceResult().getLocation();
        final BlockPos pos = new BlockPos(hit.x, hit.y, hit.z);
        if (shouldBlock(projectile, pos, projectile.getLevel())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: ProjectileImpact " + projectile.getType() + " at " + pos);
            event.setCanceled(true);
            projectile.discard();
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(final EntityJoinWorldEvent event) {
        final Entity entity = event.getEntity();
        if (!isPneumaticCraftEntity(entity)) return;
        final BlockPos pos = entity.blockPosition();
        if (shouldBlock(entity, pos, event.getWorld())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: EntityJoinWorld " + entity.getType() + " at " + pos);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (!isPneumaticCraftItem(event.getItemStack())) return;
        if (shouldBlock(event.getPlayer(), event.getPlayer().blockPosition(), event.getWorld())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: RightClickItem " + event.getItemStack().getItem());
            event.setCanceled(true);
            notifyDeny(event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final boolean handIsPnc = isPneumaticCraftItem(event.getItemStack());
        final boolean targetIsPnc = isPneumaticCraftBlock(event.getWorld().getBlockState(event.getPos()));
        if (!handIsPnc && !targetIsPnc) return;
        if (shouldBlock(event.getPlayer(), event.getPos(), event.getWorld())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: RightClickBlock at " + event.getPos());
            event.setCanceled(true);
            notifyDeny(event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        if (!isPneumaticCraftItem(event.getItemStack())) return;
        if (shouldBlock(event.getPlayer(), event.getPos(), event.getWorld())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: LeftClickBlock at " + event.getPos());
            event.setCanceled(true);
            notifyDeny(event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onUseItemStart(final LivingEntityUseItemEvent.Start event) {
        if (!isPneumaticCraftItem(event.getItem())) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (shouldBlock(player, player.blockPosition(), player.getLevel())) {
            SkyBlockAddon.CustomDebugMessages(LOGGER, "PNC blocked: UseItemStart " + event.getItem().getItem());
            event.setCanceled(true);
            notifyDeny(player);
        }
    }
}
