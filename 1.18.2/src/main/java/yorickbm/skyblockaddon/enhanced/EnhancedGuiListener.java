package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.network.PlayerTracker;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yorickbm.guilibrary.events.OpenMenuEvent;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;

import java.util.HashMap;
import java.util.Map;

public class EnhancedGuiListener {

    private static final Map<String, EnhancedGuiHandler> HANDLERS = new HashMap<>();

    static {
        String modId = SkyblockAddonCore.MOD_ID;
        HANDLERS.put(modId + ":overview", IslandHubGui::open);
        HANDLERS.put(modId + ":settings", IslandHubGui::open);
        HANDLERS.put(modId + ":travel", (p, d) -> ScrollableListGui.open(p, d, "travel"));
        HANDLERS.put(modId + ":members", (p, d) -> ScrollableListGui.open(p, d, "members"));
        HANDLERS.put(modId + ":biomes", (p, d) -> ScrollableListGui.open(p, d, "biomes"));
        HANDLERS.put(modId + ":groups", (p, d) -> ScrollableListGui.open(p, d, "groups"));
        HANDLERS.put(modId + ":members_group", (p, d) -> ScrollableListGui.open(p, d, "members_group"));
        HANDLERS.put(modId + ":set_group", (p, d) -> ScrollableListGui.open(p, d, "set_group"));
        HANDLERS.put(modId + ":permissions", PermissionCategoriesGui::open);
        HANDLERS.put(modId + ":set_permission", PermissionTogglesGui::open);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onOpenMenu(OpenMenuEvent event) {
        if (!PlayerTracker.isModded(event.getTarget())) return;

        String guiId = event.getGuiId();
        if (guiId == null) return;

        EnhancedGuiHandler handler = HANDLERS.get(guiId);
        if (handler != null) {
            event.setCanceled(true);
            handler.open(event.getTarget(), event.getData());
        }
    }
}
