package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.ListView;
import com.masuary.masugui.element.Panel;
import com.masuary.masugui.element.Section;
import com.masuary.masugui.element.StatusBar;
import com.masuary.masugui.element.Window;
import com.masuary.masugui.element.data.Cell;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.ListColumn;
import com.masuary.masugui.element.data.ListRow;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * "Group for &lt;player&gt;" (a click assigns the group; Visitors asks first when it removes a member) and a group's
 * players (a click opens the player's group choice).
 */
final class GroupAssignGui {

    /** Set by the group card's Players button so Back returns to the groups instead of the permission screen. */
    static final String PLAYERS_BACK_TO_GROUPS_KEY = "players_back_to_groups";

    private static final int WIDTH = 300;
    private static final int HEIGHT = 160;
    private static final int CURRENT_COLOR = 0x55FF55;
    private static final int REMOVES_COLOR = 0xFF5555;

    private GroupAssignGui() {}

    static void openSetGroup(ServerPlayer player, CompoundTag data) {
        ForgeIsland island = islandOf(data);
        if (island == null || !data.hasUUID(GuiDataKeys.PLAYER_ID)) return;
        UUID targetId = data.getUUID(GuiDataKeys.PLAYER_ID);
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        boolean visitorsRemovesMember = island.isDemotionToVisitors(targetId, SkyblockAddonCore.VISITORS_GROUP_ID);
        UUID currentGroupId = island.getGroupForEntityUUID(targetId).map(IslandGroup::getId).orElse(SkyblockAddonCore.VISITORS_GROUP_ID);
        List<IslandGroup> groups = assignableOrder(island);

        List<ListRow> rows = new ArrayList<>();
        int currentIndex = -1;
        for (int index = 0; index < groups.size(); index++) {
            IslandGroup group = groups.get(index);
            boolean isVisitors = group.getId().equals(SkyblockAddonCore.VISITORS_GROUP_ID);
            boolean isCurrent = group.getId().equals(currentGroupId);
            if (isCurrent) currentIndex = index;
            String name = group.getName() + (isCurrent ? " (current)" : "");
            ListRow row;
            if (isVisitors && visitorsRemovesMember) {
                row = ListRow.of(((ForgeIslandGroup) group).getItem(), group.getName() + ": removes from the island")
                        .withStatus(REMOVES_COLOR).withCells(Cell.of("", EnhancedDialog.VALUE_WHITE));
            } else {
                row = ListRow.of(((ForgeIslandGroup) group).getItem(), name)
                        .withCells(Cell.of(isVisitors ? "" : String.valueOf(group.getMembers().size()), EnhancedDialog.VALUE_WHITE));
                if (isCurrent) row = row.withStatus(CURRENT_COLOR);
            }
            rows.add(row);
        }

        List<KeyHint> hints = new ArrayList<>(List.of(new KeyHint("Click", "a group to assign it")));
        if (visitorsRemovesMember) hints.add(new KeyHint("Visitors", ": asks first"));
        String title = "Group for " + PlayerNames.nameOf(server, targetId);
        MasuGui gui = compactFrame("island_set_group", title, EnhancedGuiHelper.getPlayerHead(targetId),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members", data), hints);
        compactTable(gui, "groups", "Group", "Players", rows, List.of(ListColumn.of("", 8)), currentIndex)
                .emptyText(new TextComponent("No groups"))
                .onClick((p, index, click) -> {
                    if (index < 0 || index >= groups.size()) return;
                    UUID groupId = groups.get(index).getId();
                    if (groupId.equals(SkyblockAddonCore.VISITORS_GROUP_ID) && island.isDemotionToVisitors(targetId, groupId)) {
                        IslandActionResult blocker = IslandActionService.checkMoveToVisitors(p, island, targetId);
                        if (EnhancedGuiHelper.closeWhenDenied(p, blocker == IslandActionResult.SUCCESS, blocker)) return;
                        ConfirmMoveToVisitorsGui.open(p, data, island, targetId);
                        return;
                    }
                    assign(p, data, targetId, groupId);
                });
        gui.openFor(player);
    }

    /** Members first, then the custom groups, Visitors last. */
    private static List<IslandGroup> assignableOrder(ForgeIsland island) {
        List<IslandGroup> ordered = new ArrayList<>();
        if (island.getMembersGroup() != null) ordered.add(island.getMembersGroup());
        for (IslandGroup group : island.getGroups()) {
            if (!GroupListGui.isDefaultGroup(group)) ordered.add(group);
        }
        if (island.getDefaultGroup() != null) ordered.add(island.getDefaultGroup());
        return ordered;
    }

    static void assign(ServerPlayer player, CompoundTag data, UUID playerId, UUID groupId) {
        MasuGui.closeFor(player);
        ForgeIsland island = islandOf(data);
        if (island != null) {
            IslandActionResult result = IslandActionService.assignGroup(player, island, playerId, groupId);
            String playerName = PlayerNames.nameOf(Objects.requireNonNull(player.getServer()), playerId);
            if (result.succeeded()) {
                player.sendMessage(IslandActionService.groupAssignedMessage(result, playerName, island.getGroup(groupId).getName()), player.getUUID());
            } else {
                IslandActionService.sendFailure(player, result, playerName);
            }
        }
        GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:members", data);
    }

    static void openGroupPlayers(ServerPlayer player, CompoundTag data) {
        ForgeIsland island = islandOf(data);
        if (island == null || !data.hasUUID(GuiDataKeys.GROUP_ID)) return;
        IslandGroup group = island.getGroup(data.getUUID(GuiDataKeys.GROUP_ID));
        if (group == null) return;
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        List<UUID> players = List.copyOf(group.getMembers());

        List<ListRow> rows = new ArrayList<>();
        for (UUID playerId : players) {
            boolean online = EnhancedGuiHelper.isOnline(server, playerId);
            rows.add(ListRow.of(EnhancedGuiHelper.getPlayerHead(playerId), PlayerNames.nameOf(server, playerId))
                    .withCells(Cell.of(EnhancedGuiHelper.onlineText(online), EnhancedGuiHelper.onlineColor(online))));
        }

        String backTarget = data.getBoolean(PLAYERS_BACK_TO_GROUPS_KEY) ? "skyblockaddon:groups" : "skyblockaddon:permissions";
        MasuGui gui = compactFrame("island_group_players", group.getName() + " players", ((ForgeIslandGroup) group).getItem(),
                p -> GUILibraryRegistry.openGUIForPlayer(p, backTarget, data),
                List.of(new KeyHint("Click", "a player to change their group")));
        compactTable(gui, "players", "Player", "", rows, List.of(ListColumn.of("Status", 8)), -1)
                .emptyText(new TextComponent("Nobody is in this group"))
                .onClick((p, index, click) -> {
                    if (index < 0 || index >= players.size()) return;
                    CompoundTag playerData = PlayerListGui.withMemberSelected(data, players.get(index));
                    MasuGui.closeFor(p);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:set_group", playerData);
                });
        gui.openFor(player);
    }

    private static MasuGui compactFrame(String guiId, String title, ItemStack fallbackIcon, Consumer<ServerPlayer> onBack, List<KeyHint> hints) {
        MasuGui gui = MasuGui.create(guiId)
                .title(new TextComponent(title))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_6);
        gui.add(new Window("window", WIDTH, HEIGHT).title(new TextComponent(title)).accent(EnhancedDialog.ACCENT)
                .fallbackIcon(fallbackIcon).onBack(onBack));
        gui.add(new StatusBar("status", 1, HEIGHT - 13, WIDTH - 2).hints(hints));
        return gui;
    }

    private static ListView compactTable(MasuGui gui, String listId, String header, String headerRight, List<ListRow> rows,
                                         List<ListColumn> columns, int selectedIndex) {
        gui.add(new Panel("list_card", 1, 21, WIDTH - 2, HEIGHT - 36).style(Panel.Style.CARD));
        gui.add(new Section("list_header", 3, 25, WIDTH - 6).label(new TextComponent(header)).right(new TextComponent(headerRight)));
        ListView list = new ListView(listId, 3, 38, WIDTH - 7, HEIGHT - 58).rowHeight(DetailListScreen.ROW_HEIGHT)
                .rows(rows).columns(columns).selected(selectedIndex);
        gui.add(list);
        return list;
    }

    private static ForgeIsland islandOf(CompoundTag data) {
        if (!data.hasUUID(GuiDataKeys.ISLAND_ID)) return null;
        return (ForgeIsland) IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
    }
}
