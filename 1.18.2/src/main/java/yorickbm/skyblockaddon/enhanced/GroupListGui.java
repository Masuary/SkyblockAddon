package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.data.Cell;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.KeyValueRow;
import com.masuary.masugui.element.data.ListColumn;
import com.masuary.masugui.element.data.ListRow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * The island's groups as one table (default and custom) beside the selected group's card: Permissions, its Players and,
 * for custom groups, Remove. The selected group is {@link GuiDataKeys#GROUP_ID} in the gui data.
 */
final class GroupListGui {

    private GroupListGui() {}

    static boolean isDefaultGroup(IslandGroup group) {
        return group.getId().equals(SkyblockAddonCore.MEMBERS_GROUP_ID) || group.getId().equals(SkyblockAddonCore.VISITORS_GROUP_ID);
    }

    static void open(ServerPlayer player, CompoundTag data) {
        if (!data.hasUUID(GuiDataKeys.ISLAND_ID)) return;
        ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;

        List<IslandGroup> groups = tableOrder(island);
        int selected = DetailListScreen.selectedIndex(groups,
                group -> data.hasUUID(GuiDataKeys.GROUP_ID) && group.getId().equals(data.getUUID(GuiDataKeys.GROUP_ID)));
        List<ListRow> rows = new ArrayList<>();
        for (IslandGroup group : groups) {
            boolean isDefault = isDefaultGroup(group);
            rows.add(ListRow.of(((ForgeIslandGroup) group).getItem(), group.getName())
                    .withCells(isDefault ? Cell.of("Default", EnhancedDialog.VALUE_GRAY) : Cell.of("Custom", EnhancedDialog.VALUE_AQUA),
                            playersCell(group)));
        }

        DetailListScreen.Geometry geometry = DetailListScreen.PLAYERS_WITH_TOOLBAR;
        String count = groups.size() + " groups";
        MasuGui gui = DetailListScreen.frame("island_groups", geometry, "Groups", count, new ItemStack(Items.WRITABLE_BOOK),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:settings", data),
                List.of(new KeyHint("Click", "a group for details")), count);
        gui.add(new Button("create_group", 5, 22, 110, 17).style(ButtonStyle.SECONDARY)
                .label(new TextComponent("+ Create group"))
                .onClick(p -> ConfirmCreateGroupGui.open(p, data)).fallbackSlot(DetailListScreen.TOOLBAR_FALLBACK_SLOT));
        gui.add(new Label("create_hint", 121, 26).text(new TextComponent("Hold an item: it becomes the icon"))
                .role(Label.Role.MUTED).maxWidth(270).enhancedOnly());
        DetailListScreen.table(gui, geometry, "groups", "Group", rows,
                        List.of(ListColumn.of("Type", 70), ListColumn.of("Players", 8)), selected)
                .onClick((p, index, click) -> {
                    if (index < 0 || index >= groups.size()) return;
                    open(p, withGroup(data, groups.get(index)));
                });
        DetailListScreen.detail(gui, geometry, selected < 0 ? null : detail(island, data, groups.get(selected)), "No group selected");
        gui.openFor(player);
    }

    /** Members and Visitors first, then the custom groups. */
    private static List<IslandGroup> tableOrder(ForgeIsland island) {
        List<IslandGroup> ordered = new ArrayList<>();
        if (island.getMembersGroup() != null) ordered.add(island.getMembersGroup());
        if (island.getDefaultGroup() != null) ordered.add(island.getDefaultGroup());
        for (IslandGroup group : island.getGroups()) {
            if (!isDefaultGroup(group)) ordered.add(group);
        }
        return ordered;
    }

    private static Cell playersCell(IslandGroup group) {
        return group.getId().equals(SkyblockAddonCore.VISITORS_GROUP_ID)
                ? Cell.of("all others", EnhancedDialog.VALUE_GRAY)
                : Cell.of(String.valueOf(group.getMembers().size()), EnhancedDialog.VALUE_WHITE);
    }

    private static DetailListScreen.Detail detail(ForgeIsland island, CompoundTag data, IslandGroup group) {
        List<Permission> permissions = PermissionManager.getInstance().getPermissions();
        long allowed = permissions.stream().filter(permission -> group.canDo(permission.getId())).count();
        Cell players = playersCell(group);
        List<KeyValueRow> facts = List.of(
                new KeyValueRow(new TextComponent("Players"), players.text(), players.color()),
                KeyValueRow.of("Allowed rules", allowed + " of " + permissions.size(), EnhancedDialog.VALUE_WHITE));

        CompoundTag groupData = withGroup(data, group);
        CompoundTag playersData = groupData.copy();
        playersData.putBoolean(GroupAssignGui.PLAYERS_BACK_TO_GROUPS_KEY, true);
        List<DetailListScreen.DetailButton> row = new ArrayList<>();
        if (!isDefaultGroup(group)) {
            row.add(DetailListScreen.DetailButton.of("remove", "Remove", ButtonStyle.DANGER,
                    p -> ConfirmRemoveGroupGui.open(p, groupData, island, (ForgeIslandGroup) group)));
        }
        row.add(DetailListScreen.DetailButton.of("players", "Players", ButtonStyle.SECONDARY,
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members_group", playersData)));
        return new DetailListScreen.Detail(((ForgeIslandGroup) group).getItem(), List.of(), group.getName(), facts, List.of(row),
                DetailListScreen.DetailButton.of("permissions", "Permissions", ButtonStyle.PRIMARY,
                        p -> {
                            MasuGui.closeFor(p);
                            GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:permissions", groupData);
                        }));
    }

    private static CompoundTag withGroup(CompoundTag data, IslandGroup group) {
        CompoundTag withGroup = data.copy();
        withGroup.putUUID(GuiDataKeys.GROUP_ID, group.getId());
        withGroup.remove(GroupAssignGui.PLAYERS_BACK_TO_GROUPS_KEY);
        return withGroup;
    }
}
