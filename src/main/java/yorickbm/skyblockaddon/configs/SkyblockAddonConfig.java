package yorickbm.skyblockaddon.configs;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashMap;

public class SkyblockAddonConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static final HashMap<String, ForgeConfigSpec.ConfigValue<String>> values = new HashMap<>();

    static {

        BUILDER.push("config");

        register("island.spawn.height", "110", "Define bottom height of your island.NBT to spawn at.");
        register("island.size.radius", "400", "Set island size radius. (ONLY modify when NO ISLANDS have been created yet).");
        register("island.particles.border", "TRUE", "Enable/Disable the particle border that is generated at island buildable edge.");
        register("mojang.API", "https://sessionserver.mojang.com/session/minecraft/profile/%s", "Mojang API used for skin rendering in UI's");

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private static void register(String key, String value, String comment) {
        values.put(
                key,
                BUILDER.comment(comment)
                        .define(key, value)
        );
    }
    public static String getForKey(String key) {
        if(!values.containsKey(key))
            return key; //Return lookup value if not found
        return values.get(key).get(); //Return value from config
    }
}
