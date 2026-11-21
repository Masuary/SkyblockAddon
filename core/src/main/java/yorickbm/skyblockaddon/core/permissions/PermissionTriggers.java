package yorickbm.skyblockaddon.core.permissions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public final class PermissionTriggers {
    public static final String ON_TRAMPLE = "onTrample";
    public static final String ON_ENDER_PEARL = "onEnderPearl";
    public static final String ON_CHORUS_FRUIT = "onChorusFruit";
    public static final String ON_SLEEP_IN_BED = "onSleepInBed";
    public static final String ON_XP = "onXp";
    public static final String ON_BONEMEAL = "onBonemeal";
    public static final String ON_PICKUP = "onPickup";
    public static final String ON_DROP = "onDrop";
    public static final String ON_BUCKET = "onBucket";
    public static final String ON_MOUNT = "onMount";
    public static final String ON_RIGHT_CLICK_ENTITY = "onRightClickEntity";
    public static final String ON_RIGHT_CLICK_BLOCK = "onRightClickBlock";
    public static final String ON_PLACE_BLOCK = "onPlaceBlock";
    public static final String ON_BREAK_BLOCK = "onBreakBlock";
    public static final String ON_PORTAL_IGNITION = "onPortalIgnition";
    public static final String ON_RIGHT_CLICK_EMPTY = "onRightClickEmpty";
    public static final String ON_RIGHT_CLICK_ITEM = "onRightClickItem";
    public static final String ON_LEFT_CLICK_BLOCK = "onLeftClickBlock";
    public static final String ON_LEFT_CLICK_EMPTY = "onLeftClickEmpty";
    public static final String ON_ATTACK = "onAttack";
    public static final String ON_USE = "onUse";
    public static final String ON_PLAYER_CHANGED_DIMENSION = "onPlayerChangedDimension";
    public static final String ON_HOSTILE_MOB_SPAWN = "OnHostileMobSpawn";
    public static final String ON_PASSIVE_MOB_SPAWN = "OnPassiveMobSpawn";
    public static final String ON_QUARK_PICKARANG = "onQuarkPickarangMixin";
    public static final String ON_EASY_VILLAGERS_BLOCK_INTERACT = "onEVBlockInteract";
    public static final String ON_PICKUP_VILLAGER = "onPickupVillager";
    public static final String ON_EASY_PIGLINS_BLOCK_INTERACT = "onEPBlockInteract";
    public static final String ON_PICKUP_PIGLIN = "onPickupPiglin";
    public static final String ON_ENTER_NETHER_PORTAL = "onEnterNetherPortal";
    public static final String ON_ENTER_VAULT_PORTAL = "onEnterVaultPortal";
    public static final String ON_ELEVATOR_USE = "onElevatorUse";

    private static final List<String> KNOWN_TRIGGERS = Arrays.stream(PermissionTriggers.class.getDeclaredFields())
            .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
            .map(PermissionTriggers::triggerValue)
            .toList();

    private PermissionTriggers() {
    }

    /**
     * Every trigger the mod fires a permission check for (the String constants of this class).
     */
    public static List<String> knownTriggers() {
        return KNOWN_TRIGGERS;
    }

    private static String triggerValue(final Field triggerConstant) {
        try {
            return (String) triggerConstant.get(null);
        } catch (final IllegalAccessException inaccessibleConstant) {
            throw new IllegalStateException("Cannot read trigger constant " + triggerConstant.getName(), inaccessibleConstant);
        }
    }
}
