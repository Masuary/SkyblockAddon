package yorickbm.skyblockaddon.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.core.JSON.PermissionDataJson;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.islands.InteractionHandler;
import yorickbm.skyblockaddon.util.BlockInteractability;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PermissionEvents {
    private static final String DIMENSION_RULE_PREFIX = "dimension:";

    private static final List<TriggerRule<?>> SIMPLE_TRIGGER_RULES = List.of(
            TriggerRule.firstPermission(BlockEvent.FarmlandTrampleEvent.class, PermissionTriggers.ON_TRAMPLE, BlockEvent.FarmlandTrampleEvent::getEntity),
            TriggerRule.firstPermission(EntityTeleportEvent.EnderPearl.class, PermissionTriggers.ON_ENDER_PEARL, EntityTeleportEvent.EnderPearl::getEntity),
            TriggerRule.firstPermission(EntityTeleportEvent.ChorusFruit.class, PermissionTriggers.ON_CHORUS_FRUIT, EntityTeleportEvent.ChorusFruit::getEntity),
            TriggerRule.firstPermission(PlayerSleepInBedEvent.class, PermissionTriggers.ON_SLEEP_IN_BED, PlayerSleepInBedEvent::getEntity),
            TriggerRule.firstPermission(PlayerXpEvent.PickupXp.class, PermissionTriggers.ON_XP, PlayerXpEvent.PickupXp::getEntity),
            TriggerRule.firstPermission(BonemealEvent.class, PermissionTriggers.ON_BONEMEAL, BonemealEvent::getEntity),

            TriggerRule.matchedAgainstData(EntityItemPickupEvent.class, PermissionTriggers.ON_PICKUP, EntityItemPickupEvent::getEntity,
                    event -> itemId(event.getItem().getItem().getItem()), PermissionDataJson::getItemsData),
            TriggerRule.matchedAgainstData(ItemTossEvent.class, PermissionTriggers.ON_DROP, ItemTossEvent::getPlayer,
                    event -> itemId(event.getEntityItem().getItem().getItem()), PermissionDataJson::getItemsData)
                    .withDenialEffect(PermissionEvents::returnTossedItem),
            TriggerRule.matchedAgainstData(FillBucketEvent.class, PermissionTriggers.ON_BUCKET, FillBucketEvent::getEntity,
                    PermissionEvents::bucketItemId, PermissionDataJson::getItemsData),
            TriggerRule.matchedAgainstData(EntityMountEvent.class, PermissionTriggers.ON_MOUNT, EntityMountEvent::getEntity,
                    event -> entityTypeId(event.getEntityBeingMounted()), PermissionDataJson::getEntitiesData),
            TriggerRule.matchedAgainstData(AttackEntityEvent.class, PermissionTriggers.ON_ATTACK, AttackEntityEvent::getPlayer,
                    event -> entityTypeId(event.getTarget()), PermissionDataJson::getEntitiesData),
            TriggerRule.matchedAgainstData(LivingEntityUseItemEvent.class, PermissionTriggers.ON_USE, LivingEntityUseItemEvent::getEntity,
                    event -> itemId(event.getItem().getItem()), PermissionDataJson::getItemsData),
            TriggerRule.matchedAgainstData(EntityTravelToDimensionEvent.class, PermissionTriggers.ON_PLAYER_CHANGED_DIMENSION, EntityTravelToDimensionEvent::getEntity,
                    event -> event.getDimension().location().toString(), PermissionEvents::dimensionRules)
                    .withDenialEffect((event, player) -> InteractionHandler.pushPlayerBack(player)),

            TriggerRule.playerInteraction(PlayerInteractEvent.EntityInteract.class, PermissionTriggers.ON_RIGHT_CLICK_ENTITY),
            TriggerRule.playerInteraction(PlayerInteractEvent.RightClickEmpty.class, PermissionTriggers.ON_RIGHT_CLICK_EMPTY),
            TriggerRule.playerInteraction(PlayerInteractEvent.RightClickItem.class, PermissionTriggers.ON_RIGHT_CLICK_ITEM),
            TriggerRule.playerInteraction(PlayerInteractEvent.LeftClickBlock.class, PermissionTriggers.ON_LEFT_CLICK_BLOCK),
            TriggerRule.playerInteraction(PlayerInteractEvent.LeftClickEmpty.class, PermissionTriggers.ON_LEFT_CLICK_EMPTY)
    );

    public static void register(final IEventBus eventBus) {
        eventBus.register(new PermissionEvents());
        SIMPLE_TRIGGER_RULES.forEach(rule -> rule.register(eventBus));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            final String trigger = BlockInteractability.isRightClickInteraction(event.getWorld(), event.getPos(), event.getPlayer(), event.getHand())
                    ? PermissionTriggers.ON_RIGHT_CLICK_BLOCK
                    : PermissionTriggers.ON_PLACE_BLOCK;

            final ServerPlayer player = (ServerPlayer) event.getPlayer();
            if (InteractionHandler.checkPlayerInteraction(standingOn, player, (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(), trigger)) {
                InteractionHandler.denyBlockClick(event, player);
            }
        }

        denyNetherPortalIgnition(event, event.getEntity(), event.getWorld(), event.getPos(), player -> event.getItemStack());
    }

    @SubscribeEvent
    public void onPlaceBlock(final BlockEvent.EntityPlaceEvent event) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (!InteractionHandler.verifyEntity(event.getEntity(), standingOn).asBoolean()) {
            final ServerPlayer player = (ServerPlayer) event.getEntity();
            if (InteractionHandler.checkPlayerInteraction(standingOn, player, (ServerLevel) event.getWorld(), event.getPos(), player.getMainHandItem(), PermissionTriggers.ON_PLACE_BLOCK)) {
                InteractionHandler.deny(event, player);
            }
        }

        denyNetherPortalIgnition(event, event.getEntity(), event.getWorld(), event.getPos(), ServerPlayer::getMainHandItem);
    }

    @SubscribeEvent
    public void onMobSpawn(final LivingSpawnEvent.CheckSpawn event) {
        final EntityType<?> entityType = event.getEntity().getType();
        final Optional<SkyblockAddonWorldCapability> cap = event.getEntity().getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).resolve();
        if (cap.isEmpty()) return;

        final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(event.getEntity().blockPosition()));
        if (island == null) return;

        final boolean runFail = InteractionHandler.isDeniedByMatchData(
                island.getGroupForEntityUUID(event.getEntity().getUUID()),
                (entityType.getCategory() == MobCategory.MONSTER ? PermissionTriggers.ON_HOSTILE_MOB_SPAWN : PermissionTriggers.ON_PASSIVE_MOB_SPAWN),
                EntityType.getKey(event.getEntity().getType()).toString(),
                PermissionDataJson::getEntitiesData,
                false
        );

        if (runFail) {
            event.setResult(Event.Result.DENY);
            if (event.isCancelable()) event.setCanceled(true);
        }
    }

    private static void denyNetherPortalIgnition(final Event event, final Entity entity, final LevelAccessor world, final BlockPos position, final Function<ServerPlayer, ItemStack> heldItem) {
        final AtomicReference<Island> overworldIsland = new AtomicReference<>();
        if (InteractionHandler.verifyNetherEntity(entity, overworldIsland, position).asBoolean()) return;

        final ServerPlayer player = (ServerPlayer) entity;
        final ItemStack item = heldItem.apply(player);
        if (item.isEmpty() || item.getItem() != Items.FLINT_AND_STEEL) return;

        final BlockState state = world.getBlockState(position);
        if (state.isAir() || state.getBlock().asItem() != Items.OBSIDIAN) return;

        if (InteractionHandler.checkPlayerInteraction(overworldIsland, player, (ServerLevel) world, position, item, PermissionTriggers.ON_PORTAL_IGNITION)) {
            InteractionHandler.deny(event, player);
        }
    }

    private static boolean isDeniedByFirstPermission(final Optional<IslandGroup> group, final String trigger) {
        if (group.isEmpty()) return true;

        final List<Permission> permissions = PermissionManager.getInstance().getPermissionsForTrigger(trigger);
        if (permissions.isEmpty()) return false;
        return !group.get().canDo(permissions.get(0).getId());
    }

    private static void returnTossedItem(final ItemTossEvent event, final ServerPlayer player) {
        player.addItem(event.getEntityItem().getItem());
        player.getInventory().setChanged();
    }

    private static String bucketItemId(final FillBucketEvent event) {
        final ItemStack bucket = event.getFilledBucket() == null ? event.getEmptyBucket() : event.getFilledBucket();
        return itemId(bucket.getItem());
    }

    private static List<String> dimensionRules(final PermissionDataJson data) {
        return data.getSkyblockaddonData().stream()
                .filter(rule -> rule.startsWith(DIMENSION_RULE_PREFIX))
                .map(rule -> rule.replace(DIMENSION_RULE_PREFIX, ""))
                .collect(Collectors.toList());
    }

    private static String itemId(final Item item) {
        return Objects.requireNonNull(item.getRegistryName()).toString();
    }

    private static String entityTypeId(final Entity entity) {
        return Objects.requireNonNull(EntityType.getKey(entity.getType())).toString();
    }

    @FunctionalInterface
    private interface TriggerDecision<E extends Event> {
        boolean isDenied(E event, ServerPlayer player, AtomicReference<Island> standingOn);
    }

    /**
     * A permission check for an event whose only special handling is how its trigger is judged, plus an optional effect
     * after the denial. Registered at normal priority, like the per-event handlers it replaces.
     */
    private record TriggerRule<E extends Event>(Class<E> eventType, Function<E, Entity> actor, TriggerDecision<E> decision, BiConsumer<E, ServerPlayer> denialEffect) {

        static <E extends Event> TriggerRule<E> firstPermission(final Class<E> eventType, final String trigger, final Function<E, Entity> actor) {
            return new TriggerRule<>(eventType, actor,
                    (event, player, standingOn) -> isDeniedByFirstPermission(standingOn.get().getGroupForEntityUUID(player.getUUID()), trigger),
                    (event, player) -> { });
        }

        static <E extends Event> TriggerRule<E> matchedAgainstData(final Class<E> eventType, final String trigger, final Function<E, Entity> actor,
                                                                   final Function<E, String> matchValue, final Function<PermissionDataJson, List<String>> dataExtractor) {
            return new TriggerRule<>(eventType, actor, (event, player, standingOn) -> {
                final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(player.getUUID());
                if (group.isEmpty()) return true;
                return InteractionHandler.isDeniedByMatchData(group, trigger, matchValue.apply(event), dataExtractor, true);
            }, (event, player) -> { });
        }

        static <E extends PlayerInteractEvent> TriggerRule<E> playerInteraction(final Class<E> eventType, final String trigger) {
            return new TriggerRule<>(eventType, PlayerInteractEvent::getPlayer,
                    (event, player, standingOn) -> InteractionHandler.checkPlayerInteraction(standingOn, player, (ServerLevel) event.getWorld(), event.getPos(), event.getItemStack(), trigger),
                    (event, player) -> { });
        }

        TriggerRule<E> withDenialEffect(final BiConsumer<E, ServerPlayer> effect) {
            return new TriggerRule<>(this.eventType, this.actor, this.decision, effect);
        }

        void register(final IEventBus eventBus) {
            eventBus.addListener(EventPriority.NORMAL, false, this.eventType, this::handle);
        }

        private void handle(final E event) {
            final Entity entity = this.actor.apply(event);
            final AtomicReference<Island> standingOn = new AtomicReference<>();
            if (InteractionHandler.verifyEntity(entity, standingOn).asBoolean()) return;

            final ServerPlayer player = (ServerPlayer) entity;
            if (this.decision.isDenied(event, player, standingOn)) {
                InteractionHandler.deny(event, player);
                this.denialEffect.accept(event, player);
            }
        }
    }
}
