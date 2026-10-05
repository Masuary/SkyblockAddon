package yorickbm.skyblockaddon.enhanced;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.ListView;
import com.masuary.masugui.element.Section;
import com.masuary.masugui.element.Sidebar;
import com.masuary.masugui.element.StatusBar;
import com.masuary.masugui.element.Window;
import com.masuary.masugui.element.data.Cell;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.ListColumn;
import com.masuary.masugui.element.data.ListRow;
import com.masuary.masugui.element.data.SidebarEntry;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.JSON.ItemStackJson;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** A group's permissions: categories on the left, the category's rules on the right; a click toggles a rule. */
public final class PermissionTogglesGui {

    private static final int WIDTH = 356;
    private static final int HEIGHT = 214;
    private static final String[][] CATEGORIES = {
            {"general", "General"}, {"transport", "Transport"}, {"redstone", "Redstone"}, {"storage", "Storage"},
            {"interactables", "Interactables"}, {"vaulthunters", "Vault Hunters"}, {"mods", "Mods"}};
    private static final String[] ADMIN_CATEGORY = {"admin_controls", "Admin"};

    private PermissionTogglesGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id") || !data.contains("group_id")) return;
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;
        IslandGroup group = island.getGroup(data.getUUID("group_id"));
        if (group == null) return;
        String categoryId = data.getCompound(GUILibraryRegistry.MOD_ID).getString("category_id");
        open(player, data, categoryId.isEmpty() ? CATEGORIES[0][0] : categoryId, group);
    }

    static List<String[]> categoriesFor(ServerPlayer player) {
        List<String[]> categories = new ArrayList<>(List.of(CATEGORIES));
        if (player.hasPermissions(2)) categories.add(ADMIN_CATEGORY);
        return categories;
    }

    public static void open(ServerPlayer player, CompoundTag data, String categoryId, IslandGroup group) {
        List<String[]> categories = categoriesFor(player);
        int active = 0;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i)[0].equals(categoryId)) active = i;
        }
        List<Permission> permissions = PermissionManager.getInstance().getPermissionsFor(categoryId);
        long allowed = permissions.stream().filter(permission -> group.canDo(permission.getId())).count();

        MasuGui gui = MasuGui.create("permission_toggles")
                .title(new TextComponent("Permissions: " + group.getName()))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_6);
        gui.add(new Window("window", WIDTH, HEIGHT).title(new TextComponent("Permissions"))
                .subtitle(new TextComponent("Group: " + group.getName())).accent(EnhancedDialog.ACCENT)
                .onBack(p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data)));

        List<SidebarEntry> entries = new ArrayList<>();
        for (String[] category : categories) {
            List<Permission> categoryPermissions = PermissionManager.getInstance().getPermissionsFor(category[0]);
            long categoryAllowed = categoryPermissions.stream().filter(permission -> group.canDo(permission.getId())).count();
            entries.add(SidebarEntry.of(category[1], ItemStack.EMPTY, categoryAllowed + "/" + categoryPermissions.size()));
        }
        gui.add(new Sidebar("category", 1, 21, 112, 150).rowHeight(16).entries(entries).active(active)
                .onSelect((p, index, click) -> {
                    if (index >= 0 && index < categories.size()) open(p, data, categories.get(index)[0], group);
                }));

        gui.add(new Section("rules_header", 113, 21, 242).label(new TextComponent(categories.get(active)[1]))
                .right(new TextComponent(allowed + " of " + permissions.size() + " allowed")));
        List<ListRow> rows = new ArrayList<>();
        for (Permission permission : permissions) {
            boolean enabled = group.canDo(permission.getId());
            rows.add(ListRow.of(new ItemStack(enabled ? Items.LIME_DYE : Items.GRAY_DYE), extractDisplayName(permission))
                    .withStatus(enabled ? 0x55FF55 : 0xFF5555)
                    .withCells(Cell.of(enabled ? "Allowed" : "Denied", enabled ? 0x55FF55 : 0xFF5555))
                    .withTooltip(buildTooltip(permission)));
        }
        gui.add(new ListView("rules", 113, 34, 242, 140).rows(rows).columns(List.of(ListColumn.of("", 8)))
                .emptyText(new TextComponent("No rules in this category"))
                .fallbackHints(List.of(new KeyHint("Click", "to allow or deny")))
                .onClick((p, index, click) -> {
                    if (index < 0 || index >= permissions.size()) return;
                    Permission permission = permissions.get(index);
                    group.setPermission(permission.getId(), !group.canDo(permission.getId()));
                    open(p, data, categoryId, group);
                }));

        gui.add(new Button("allow_all", 120, 179, 70, 15).style(ButtonStyle.SECONDARY).label(new TextComponent("Allow all"))
                .onClick(p -> setAll(p, data, categoryId, group, permissions, true)).fallbackSlot(47));
        gui.add(new Button("deny_all", 194, 179, 70, 15).style(ButtonStyle.SECONDARY).label(new TextComponent("Deny all"))
                .onClick(p -> setAll(p, data, categoryId, group, permissions, false)).fallbackSlot(48));
        gui.add(new Button("members", 278, 179, 70, 15).style(ButtonStyle.SECONDARY).label(new TextComponent("Members"))
                .onClick(p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members_group", data)).fallbackSlot(50));
        boolean defaultGroup = group.getId().equals(SkyblockAddonCore.MOD_UUID) || group.getId().equals(SkyblockAddonCore.MOD_UUID2);
        if (!defaultGroup) {
            Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
            gui.add(new Button("remove_group", 6, 179, 100, 15).style(ButtonStyle.DANGER).label(new TextComponent("Remove group"))
                    .onClick(p -> ConfirmRemoveGroupGui.open(p, data, island, group)).fallbackSlot(52));
        }
        gui.add(new StatusBar("status", 1, HEIGHT - 13, WIDTH - 2)
                .hints(List.of(new KeyHint("Click", "Allow / deny"), new KeyHint("Hover", "What it covers")))
                .right(new TextComponent("Changes apply instantly")));
        gui.openFor(player);
    }

    private static void setAll(ServerPlayer player, CompoundTag data, String categoryId, IslandGroup group,
                               List<Permission> permissions, boolean allowed) {
        for (Permission permission : permissions) group.setPermission(permission.getId(), allowed);
        open(player, data, categoryId, group);
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

        for (String[] loreLine : itemJson.getLore()) {
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
        } catch (Exception ignored) {}
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
