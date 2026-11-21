package yorickbm.skyblockaddon.configs;

import net.minecraftforge.common.ForgeConfigSpec;
import yorickbm.skyblockaddon.core.islands.IslandSlot;
import yorickbm.skyblockaddon.util.BlockInteractability;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SkyblockAddonConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static final HashMap<String, ForgeConfigSpec.ConfigValue<String>> values = new HashMap<>();
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> reservedSlots;

    static {

        BUILDER.push("config");

        register("island.spawn.height", "110", "Define bottom height of your island.NBT to spawn at.");
        register("island.particles.border", "TRUE", "Enable/Disable the particle border that is generated at island buildable edge.");
        register("island.create.cooldown", "5", "Cooldown before /island create may be executed again. (0 for no cooldown).");
        register("island.create.enabled", "TRUE", "TRUE lets players run /island create on this server, FALSE refuses it (servers that must not hand out islands).");
        register("island.create.blocks_per_tick", "4096", "Maximum island structure blocks placed per server tick while an island is built; islands are built one at a time.");
        reservedSlots = BUILDER
                .comment("Island grid slots /island create never uses, as \"x,z\" block coordinates of the slot center (snapped to the nearest slot). /island admin reserve adds more per world.")
                .<String>defineListAllowEmpty(List.of("island", "reserved_slots"), () -> List.of("0,0"), value -> value instanceof String);


        register("permissions.debug", "FALSE", "Print debug messages on island permission events into console. This helps verifying and adding new permissions into your configuration.");
        register(BlockInteractability.INTERACTABLE_BLOCKS_CONFIG_KEY, "", "Comma-separated block ids (for example minecraft:jukebox) whose right-clicks are always checked as onRightClickBlock (interaction). Wins over non_interactable_blocks.");
        register(BlockInteractability.NON_INTERACTABLE_BLOCKS_CONFIG_KEY, "", "Comma-separated block ids whose right-clicks are always checked as onPlaceBlock (placing against the block).");

        register("purge.batch_time", "15", "Amount of ticks (20 ticks +- 1 second) in between filling batch buffer.");
        register("purge.concurrent", "1", "Amount of chunks allowed to be processed concurrent.");
        register("purge.blocks", "800", "Amount of blocks to remove per execution.");


        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private static void register(final String key, final String value, final String comment) {
        values.put(
                key,
                BUILDER.comment(comment)
                        .define(key, value)
        );
    }

    public static String getForKey(final String key) {
        if (!values.containsKey(key))
            return key; //Return lookup value if not found
        return values.get(key).get(); //Return value from config
    }

    public static Set<IslandSlot> getReservedSlots() {
        return reservedSlots.get().stream().map(SkyblockAddonConfig::parseReservedSlot).collect(Collectors.toUnmodifiableSet());
    }

    private static IslandSlot parseReservedSlot(final String configuredSlot) {
        try {
            return IslandSlot.parse(configuredSlot);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException("SkyblockAddon config island.reserved_slots: " + exception.getMessage(), exception);
        }
    }

    public static boolean isIslandCreateEnabled() {
        return getStrictBoolean("island.create.enabled");
    }

    public static int getIslandBlocksPerTick() {
        final String configuredValue = getForKey("island.create.blocks_per_tick");
        final String problem = "SkyblockAddon config island.create.blocks_per_tick must be a positive whole number, was '" + configuredValue + "'";
        final int blocksPerTick;
        try {
            blocksPerTick = Integer.parseInt(configuredValue.trim());
        } catch (final NumberFormatException exception) {
            throw new IllegalStateException(problem, exception);
        }
        if (blocksPerTick <= 0) throw new IllegalStateException(problem);
        return blocksPerTick;
    }

    /**
     * Parse every island creation setting so a typo stops the server at start-up instead of
     * silently handing out reserved slots or ignoring the create switch.
     */
    public static void validateIslandCreationSettings() {
        getReservedSlots();
        isIslandCreateEnabled();
        getIslandBlocksPerTick();
    }

    private static boolean getStrictBoolean(final String key) {
        final String configuredValue = getForKey(key).trim();
        if (configuredValue.equalsIgnoreCase("TRUE")) return true;
        if (configuredValue.equalsIgnoreCase("FALSE")) return false;
        throw new IllegalStateException("SkyblockAddon config " + key + " must be TRUE or FALSE, was '" + configuredValue + "'");
    }

    public static boolean setValueForKey(final String key, final String value) {
        if (!values.containsKey(key)) return false;
        values.get(key).set(value);
        return true;
    }

}