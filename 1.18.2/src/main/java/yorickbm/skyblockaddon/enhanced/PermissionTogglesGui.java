package yorickbm.skyblockaddon.enhanced;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.*;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.Checkbox;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.Panel;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.guilibrary.util.ConditionEvaluator;
import yorickbm.skyblockaddon.core.JSON.ItemStackJson;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.util.LoreModelBridge;
import yorickbm.skyblockaddon.islands.IslandAdministrationService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class PermissionTogglesGui {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int WIDTH = 240;
    private static final int CHECKBOX_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 30;
    private static final int NAV_HEIGHT = 18;
    private static final int MIN_HEIGHT = 120;
    private static final int MAX_HEIGHT = 300;

    private PermissionTogglesGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id") || !data.contains("group_id")) return;

        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;
        if (!EnhancedGuiHelper.canManageIsland(player, island)) return;
        IslandGroup group = island.getGroup(data.getUUID("group_id"));
        if (group == null) return;

        String categoryId = data.getCompound(GUILibraryRegistry.MOD_ID).getString("category_id");
        open(player, data, categoryId, group);
    }

    public static void open(ServerPlayer player, CompoundTag data, String categoryId, IslandGroup group) {
        if (!data.contains("island_id")) return;
        final Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
        if (!EnhancedGuiHelper.canManageIsland(player, island)) return;
        List<Permission> permissions = PermissionManager.getInstance().getPermissionsFor(categoryId);

        String categoryName = prettifyCategory(categoryId);
        int permCount = permissions.size();
        int contentHeight = HEADER_HEIGHT + permCount * (CHECKBOX_HEIGHT + 2) + 4 + NAV_HEIGHT + 6;
        int totalHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, contentHeight));

        int navY = totalHeight - NAV_HEIGHT - 4;
        int navDivY = navY - 4;

        MasuGui gui = MasuGui.create("permission_toggles")
                .title(new TextComponent(categoryName + " Permissions"))
                .size(WIDTH, totalHeight)
                .fallbackType(FallbackType.CHEST_3);

        gui.add(new Panel("bg", 0, 0, WIDTH, totalHeight)
                .color(0xE8181818).border(0x333333));

        gui.add(new Button("close_btn", WIDTH - 16, 2, 12, 12)
                .label(new TextComponent("X")).backgroundColor(0xFFAA4444).flat()
                .onClick(MasuGui::closeFor));

        gui.add(new Label("title", WIDTH / 2, 6)
                .text(new TextComponent(categoryName + " Permissions").withStyle(ChatFormatting.GOLD))
                .centered().scale(1.0f).shadow(true));

        gui.add(new Label("group_info", WIDTH / 2, 18)
                .text(new TextComponent("Group: " + group.getName()))
                .color(0xFFAAAAAA).centered().scale(0.7f));

        gui.add(new Divider("header_div", 8, HEADER_HEIGHT - 2, WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        int y = HEADER_HEIGHT;
        for (int i = 0; i < permissions.size(); i++) {
            Permission perm = permissions.get(i);
            boolean enabled = group.canDo(perm.getId());
            String permName = extractDisplayName(perm);

            Checkbox checkbox = new Checkbox("perm_" + i, 14, y)
                    .checked(enabled)
                    .label(new TextComponent(permName))
                    .checkColor(0xFF00CC00).boxColor(0xFF444444)
                    .onToggle((p, checked) -> {
                        if (!IslandAdministrationService.setPermission(
                                p, island, group.getId(), perm.getId(), checked)) {
                            open(p, data, categoryId, group);
                        }
                    });

            List<Component> tooltipLines = buildTooltip(perm);
            if (!tooltipLines.isEmpty()) {
                checkbox.tooltip(tooltipLines);
            }

            gui.add(checkbox);
            y += CHECKBOX_HEIGHT + 2;
        }

        gui.add(new Divider("nav_div", 8, navDivY, WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        gui.add(new Button("back_btn", 10, navY, 32, 14)
                .label(new TextComponent("Back")).backgroundColor(0xFFAA4444).flat()
                .onClick(p -> {
                    MasuGui.closeFor(p);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:permissions", data);
                }));

        gui.openFor(player);
    }

    private static String extractDisplayName(Permission perm) {
        ItemStackJson itemJson = perm.getItem();
        if (itemJson != null && itemJson.getDisplay_name() != null && itemJson.getDisplay_name().length > 0) {
            StringBuilder nameBuilder = new StringBuilder();
            for (String jsonComponent : itemJson.getDisplay_name()) {
                String text = extractTextFromJson(jsonComponent);
                if (text != null) nameBuilder.append(text);
            }
            String name = nameBuilder.toString().trim();
            if (!name.isEmpty()) return name;
        }
        return prettifyPermissionId(perm.getId());
    }

    private static List<Component> buildTooltip(Permission perm) {
        List<Component> lines = new ArrayList<>();

        String containsInfo = extractContainsInfo(perm);
        if (containsInfo != null) {
            lines.add(new TextComponent(containsInfo).withStyle(ChatFormatting.GRAY));
        }

        return lines;
    }

    private static String extractContainsInfo(Permission perm) {
        ItemStackJson itemJson = perm.getItem();
        if (itemJson == null || itemJson.getLore() == null) return null;

        final ConditionEvaluator.Context context = new ConditionEvaluator.Context() {
            @Override
            public boolean isAdmin() {
                return false;
            }

            @Override
            public boolean isOp() {
                return false;
            }

            @Override
            public boolean isPart() {
                return false;
            }
        };

        for (List<String> loreLine : LoreModelBridge.render(itemJson.getLore(), context)) {
            StringBuilder lineBuilder = new StringBuilder();
            boolean isContainsLine = false;

            for (String jsonComponent : loreLine) {
                String text = extractTextFromJson(jsonComponent);
                if (text != null) {
                    if (text.contains("Contains:")) isContainsLine = true;
                    lineBuilder.append(text);
                }
            }

            if (isContainsLine) {
                String full = lineBuilder.toString().trim();
                int idx = full.indexOf("Contains:");
                if (idx >= 0) {
                    return full.substring(idx).trim();
                }
            }
        }

        return null;
    }

    private static String extractTextFromJson(String jsonComponent) {
        try {
            JsonElement element = JsonParser.parseString(jsonComponent);
            if (element.isJsonObject() && element.getAsJsonObject().has("text")) {
                return element.getAsJsonObject().get("text").getAsString();
            }
        } catch (final RuntimeException exception) {
            LOGGER.debug("Could not extract text from permission display component: {}", jsonComponent, exception);
        }
        return null;
    }

    private static String prettifyCategory(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) return "Permissions";
        return Arrays.stream(categoryId.replace("_", " ").split(" "))
                .map(w -> w.substring(0, 1).toUpperCase() + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private static String prettifyPermissionId(String permissionId) {
        if (permissionId == null || permissionId.isEmpty()) return "Unknown";
        String name = permissionId;
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return Arrays.stream(name.replace("_", " ").split(" "))
                .map(w -> w.isEmpty() ? "" : w.substring(0, 1).toUpperCase() + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
