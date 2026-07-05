package yorickbm.guilibrary;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.JSON.GUIJson;
import yorickbm.guilibrary.events.OpenMenuEvent;
import yorickbm.guilibrary.util.JSON.JSONEncoder;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GUILibraryRegistry {
    public static final String MOD_ID = "GUILibrary";
    private static final Map<String, GUIType> REGISTRY = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger();

    // Private method to register a GUIType with a string key
    public static void register(final String ModId, final Path filePath) {
        try {
            final GUIJson decoded = JSONEncoder.loadFromFile(filePath, GUIJson.class);
            REGISTRY.put(ModId + ":" + decoded.getKey(), new GUIType.Builder()
                    .setRows(decoded.getRows())
                    .setTitle(decoded.getTitle())
                    .setItems(decoded.getItems())
                    .setFillers(decoded.getFillers())
                    .build());
            LOGGER.info(String.format("Loaded file '%s' into registry.", filePath.toString()));
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to load GUI " + filePath, ex);
        }
    }

    // Private method to register a GUIType with a string key
    public static void registerFolder(final String ModId, final Path filePath) {
        try {
            final Collection<GUIJson> decoded = JSONEncoder.loadFromFolder(filePath, GUIJson.class);
            final Map<String, GUIType> candidates = new HashMap<>();
            decoded.forEach(obj -> {
                final String guiId = ModId + ":" + obj.getKey();
                if (candidates.containsKey(guiId)) {
                    throw new IllegalArgumentException("Duplicate GUI ID: " + guiId);
                }
                candidates.put(guiId, new GUIType.Builder()
                        .setRows(obj.getRows())
                        .setTitle(obj.getTitle())
                        .setItems(obj.getItems())
                        .setFillers(obj.getFillers())
                        .build());
            });
            REGISTRY.clear();
            REGISTRY.putAll(candidates);
            LOGGER.info(String.format("Loaded path '%s' resulting in %s GUI(s) being loaded into registry.", filePath.toString(), REGISTRY.size()));
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to load GUI directory " + filePath, ex);
        }
    }

    // Method to get a GUI from the registry by its ID
    public static GUIType getValue(final String key) {
        return REGISTRY.get(key);
    }

    public static void openGUIForPlayer(final ServerPlayer player, final String id) {
        openGUIForPlayer(player, id, new CompoundTag());
    }
    public static void openGUIForPlayer(final ServerPlayer player, final String id, final CompoundTag data) {
        MinecraftForge.EVENT_BUS.post(new OpenMenuEvent(id, player, data));
    }

    public static int getGuis() {
        return REGISTRY.size();
    }
}
