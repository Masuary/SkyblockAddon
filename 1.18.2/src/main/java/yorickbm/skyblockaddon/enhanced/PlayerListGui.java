package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.data.Cell;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.KeyValueRow;
import com.masuary.masugui.element.data.ListColumn;
import com.masuary.masugui.element.data.ListRow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Members, visitors on the island and banned players as a table beside the selected player's card. The selected
 * player is {@link GuiDataKeys#PLAYER_ID} in the gui data, which is also what the screens the card opens read.
 */
final class PlayerListGui {

    private static final int OWNER_COLOR = 0xFFAA00;
    private static final String SELECTED_LIST_KEY = "player_list_selected_from";
    private static final String MEMBERS = "members";
    private static final String VISITORS = "visitors";
    private static final String BANS = "bans";

    private PlayerListGui() {}

    static void openMembers(ServerPlayer player, CompoundTag data) {
        ForgeIsland island = islandOf(data);
        if (island == null) return;
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        List<UUID> players = new ArrayList<>();
        if (!island.getOwner().equals(SkyblockAddonCore.NO_OWNER_ID)) players.add(island.getOwner());
        players.addAll(island.getMembers());
        int selected = selectedIndex(players, data, MEMBERS);
        boolean canManage = IslandActionService.canManage(island, player);
        boolean viewerIsOwner = island.isOwner(player.getUUID());

        List<ListRow> rows = new ArrayList<>();
        for (UUID playerId : players) {
            boolean online = EnhancedGuiHelper.isOnline(server, playerId);
            Cell group = island.isOwner(playerId) ? Cell.of("Owner", OWNER_COLOR) : Cell.of(groupName(island, playerId), EnhancedDialog.VALUE_AQUA);
            rows.add(ListRow.of(EnhancedGuiHelper.getPlayerHead(playerId), PlayerNames.nameOf(server, playerId))
                    .withCells(group, Cell.of(EnhancedGuiHelper.onlineText(online), EnhancedGuiHelper.onlineColor(online))));
        }

        String count = EnhancedGuiHelper.countOf(players.size(), "member", "members");
        MasuGui gui = DetailListScreen.frame("island_members", DetailListScreen.PLAYERS, "Members", count,
                new ItemStack(Items.PLAYER_HEAD), p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:overview", data),
                List.of(new KeyHint("Click", "a member for details")), count);
        DetailListScreen.table(gui, DetailListScreen.PLAYERS, "players", "Player", rows,
                        List.of(ListColumn.of("Group", 60), ListColumn.of("Status", 8)), selected)
                .emptyText(new TextComponent("No members yet"))
                .onClick((p, index, click) -> select(p, data, players, index, MEMBERS, PlayerListGui::openMembers));
        DetailListScreen.detail(gui, DetailListScreen.PLAYERS,
                selected < 0 ? null : memberDetail(server, island, data, players.get(selected), canManage, viewerIsOwner), "No member selected");
        gui.openFor(player);
    }

    private static DetailListScreen.Detail memberDetail(MinecraftServer server, ForgeIsland island, CompoundTag data, UUID memberId,
                                                        boolean canManage, boolean viewerIsOwner) {
        boolean isOwner = island.isOwner(memberId);
        boolean online = EnhancedGuiHelper.isOnline(server, memberId);
        List<KeyValueRow> facts = new ArrayList<>();
        if (!isOwner) facts.add(KeyValueRow.of("Group", groupName(island, memberId), EnhancedDialog.VALUE_AQUA));
        facts.add(KeyValueRow.of("Status", EnhancedGuiHelper.onlineText(online), EnhancedGuiHelper.onlineColor(online)));
        facts.add(KeyValueRow.of("Role", isOwner ? "Owner" : "Member", EnhancedDialog.VALUE_WHITE));

        CompoundTag memberData = withPlayer(data, memberId, MEMBERS);
        List<List<DetailListScreen.DetailButton>> secondary = new ArrayList<>();
        if (viewerIsOwner && !isOwner) {
            secondary.add(List.of(DetailListScreen.DetailButton.of("make_owner", "Make owner", ButtonStyle.SECONDARY,
                    p -> ConfirmTransferGui.open(p, memberData, island, memberId))));
        }
        DetailListScreen.DetailButton changeGroup = canManage && !isOwner
                ? DetailListScreen.DetailButton.of("change_group", "Change group", ButtonStyle.PRIMARY,
                        p -> {
                            MasuGui.closeFor(p);
                            GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:set_group", memberData);
                        })
                : null;
        return new DetailListScreen.Detail(EnhancedGuiHelper.getPlayerHead(memberId), List.of(), PlayerNames.nameOf(server, memberId),
                facts, secondary, changeGroup);
    }

    static void openVisitors(ServerPlayer player, CompoundTag data) {
        ForgeIsland island = islandOf(data);
        if (island == null) return;
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        List<ServerPlayer> visitors = island.visitorsStandingOn(server);
        List<UUID> visitorIds = visitors.stream().map(ServerPlayer::getUUID).toList();
        int selected = selectedIndex(visitorIds, data, VISITORS);

        List<ListRow> rows = new ArrayList<>();
        for (ServerPlayer visitor : visitors) {
            rows.add(ListRow.of(EnhancedGuiHelper.getPlayerHead(visitor.getUUID()), visitor.getGameProfile().getName())
                    .withCells(Cell.of(distanceText(player, visitor), EnhancedDialog.VALUE_WHITE)));
        }

        String count = visitors.size() + " here now";
        MasuGui gui = DetailListScreen.frame("island_visitors", DetailListScreen.PLAYERS, "Visitors", count,
                new ItemStack(Items.SPYGLASS), p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:overview", data),
                List.of(new KeyHint("Click", "a visitor for details"), new KeyHint("Ban", ": asks to confirm")), count);
        DetailListScreen.table(gui, DetailListScreen.PLAYERS, "players", "Player", rows,
                        List.of(ListColumn.of("Distance", 8)), selected)
                .emptyText(new TextComponent("No visitors on the island"))
                .onClick((p, index, click) -> select(p, data, visitorIds, index, VISITORS, PlayerListGui::openVisitors));
        DetailListScreen.detail(gui, DetailListScreen.PLAYERS,
                selected < 0 ? null : visitorDetail(player, island, data, visitors.get(selected)), "No visitor selected");
        gui.openFor(player);
    }

    private static DetailListScreen.Detail visitorDetail(ServerPlayer viewer, ForgeIsland island, CompoundTag data, ServerPlayer visitor) {
        UUID visitorId = visitor.getUUID();
        String visitorName = visitor.getGameProfile().getName();
        CompoundTag visitorData = withPlayer(data, visitorId, VISITORS);
        List<KeyValueRow> facts = List.of(
                KeyValueRow.of("Distance", distanceText(viewer, visitor), EnhancedDialog.VALUE_WHITE),
                KeyValueRow.of("Group", groupName(island, visitorId), EnhancedDialog.VALUE_AQUA));
        return new DetailListScreen.Detail(EnhancedGuiHelper.getPlayerHead(visitorId), List.of(), visitorName, facts,
                List.of(List.of(DetailListScreen.DetailButton.of("ban", "Ban", ButtonStyle.DANGER,
                        p -> VisitorActionGui.openBanConfirm(p, visitorData, island, visitorId)))),
                DetailListScreen.DetailButton.of("expel", "Expel", ButtonStyle.PRIMARY,
                        p -> VisitorActionGui.expel(p, visitorData, island, visitorId, visitorName)));
    }

    /** Blocks between the viewer and the visitor; only meaningful in the same dimension. */
    private static String distanceText(ServerPlayer viewer, ServerPlayer visitor) {
        if (viewer.getLevel() != visitor.getLevel()) return "other dimension";
        return Math.round(viewer.position().distanceTo(visitor.position())) + " blocks";
    }

    static void openBans(ServerPlayer player, CompoundTag data) {
        ForgeIsland island = islandOf(data);
        if (island == null) return;
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        List<UUID> banned = List.copyOf(island.getBannedPlayers());
        int selected = selectedIndex(banned, data, BANS);

        List<ListRow> rows = new ArrayList<>();
        for (UUID playerId : banned) {
            boolean online = EnhancedGuiHelper.isOnline(server, playerId);
            rows.add(ListRow.of(EnhancedGuiHelper.getPlayerHead(playerId), PlayerNames.nameOf(server, playerId))
                    .withCells(Cell.of(EnhancedGuiHelper.onlineText(online), EnhancedGuiHelper.onlineColor(online))));
        }

        String count = banned.size() + " banned";
        MasuGui gui = DetailListScreen.frame("island_bans", DetailListScreen.PLAYERS, "Banned players", count,
                new ItemStack(Items.IRON_BARS), p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:overview", data),
                List.of(new KeyHint("Click", "a player for details")), count);
        DetailListScreen.table(gui, DetailListScreen.PLAYERS, "players", "Player", rows,
                        List.of(ListColumn.of("Status", 8)), selected)
                .emptyText(new TextComponent("Nobody is banned"))
                .onClick((p, index, click) -> select(p, data, banned, index, BANS, PlayerListGui::openBans));
        DetailListScreen.detail(gui, DetailListScreen.PLAYERS,
                selected < 0 ? null : bannedDetail(server, island, data, banned.get(selected)), "No player selected");
        gui.openFor(player);
    }

    private static DetailListScreen.Detail bannedDetail(MinecraftServer server, ForgeIsland island, CompoundTag data, UUID bannedId) {
        boolean online = EnhancedGuiHelper.isOnline(server, bannedId);
        CompoundTag bannedData = withPlayer(data, bannedId, BANS);
        return new DetailListScreen.Detail(EnhancedGuiHelper.getPlayerHead(bannedId), List.of(), PlayerNames.nameOf(server, bannedId),
                List.of(KeyValueRow.of("Status", EnhancedGuiHelper.onlineText(online), EnhancedGuiHelper.onlineColor(online))),
                List.of(), DetailListScreen.DetailButton.of("lift_ban", "Lift ban", ButtonStyle.PRIMARY,
                        p -> ConfirmUnbanGui.open(p, bannedData, island, bannedId)));
    }

    private static ForgeIsland islandOf(CompoundTag data) {
        if (!data.hasUUID(GuiDataKeys.ISLAND_ID)) return null;
        return (ForgeIsland) IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
    }

    private static String groupName(ForgeIsland island, UUID playerId) {
        return island.getGroupForEntityUUID(playerId).map(IslandGroup::getName).orElse("Visitors");
    }

    private static int selectedIndex(List<UUID> players, CompoundTag data, String list) {
        boolean selectedInThisList = data.hasUUID(GuiDataKeys.PLAYER_ID) && list.equals(data.getString(SELECTED_LIST_KEY));
        return DetailListScreen.selectedIndexWithoutFallback(players,
                playerId -> selectedInThisList && playerId.equals(data.getUUID(GuiDataKeys.PLAYER_ID)),
                selectedInThisList);
    }

    private static CompoundTag withPlayer(CompoundTag data, UUID playerId, String list) {
        CompoundTag withPlayer = data.copy();
        withPlayer.putUUID(GuiDataKeys.PLAYER_ID, playerId);
        withPlayer.putString(SELECTED_LIST_KEY, list);
        return withPlayer;
    }

    private static void select(ServerPlayer player, CompoundTag data, List<UUID> players, int index, String list, EnhancedGuiHandler screen) {
        if (index < 0 || index >= players.size()) return;
        screen.open(player, withPlayer(data, players.get(index), list));
    }
}
