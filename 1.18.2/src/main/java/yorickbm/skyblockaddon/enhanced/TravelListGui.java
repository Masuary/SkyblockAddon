package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.Dropdown;
import com.masuary.masugui.element.TextField;
import com.masuary.masugui.element.data.Cell;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.KeyValueRow;
import com.masuary.masugui.element.data.ListColumn;
import com.masuary.masugui.element.data.ListRow;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.IslandTravelListing;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Public islands as a table with search and sort; the island card shows its facts and teleports there. */
final class TravelListGui {

    private static final String QUERY_KEY = "travel_query";
    private static final String SORT_KEY = "travel_sort";
    private static final String SELECTED_KEY = "travel_selected_island";

    private enum Sort {
        RECENTLY_ACTIVE("Recently active"),
        MOST_MEMBERS("Most members"),
        NAME("Name");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        static Sort fromIndex(int index) {
            return index >= 0 && index < values().length ? values()[index] : RECENTLY_ACTIVE;
        }
    }

    private TravelListGui() {}

    static void open(ServerPlayer player, CompoundTag data) {
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        String query = data.getString(QUERY_KEY);
        Sort sort = Sort.fromIndex(data.getInt(SORT_KEY));
        Map<UUID, String> ownerNames = new HashMap<>();
        Function<Island, String> ownerNameOf = island -> ownerNames.computeIfAbsent(island.getOwner(), owner -> PlayerNames.nameOf(server, owner));
        List<Island> destinations = sorted(IslandTravelListing.travelDestinations(IslandManager.getInstance().getIslands(),
                player.getUUID(), query, ownerNameOf), sort, ownerNameOf);
        long now = System.currentTimeMillis();
        int selected = DetailListScreen.selectedIndex(destinations,
                island -> data.hasUUID(SELECTED_KEY) && island.getId().equals(data.getUUID(SELECTED_KEY)));

        List<ListRow> rows = new ArrayList<>();
        for (Island island : destinations) {
            rows.add(ListRow.of(EnhancedGuiHelper.getPlayerHead(island.getOwner()), islandName(island, ownerNameOf))
                    .withCells(Cell.of(String.valueOf(IslandTravelListing.playerCount(island)), EnhancedDialog.VALUE_WHITE),
                            Cell.of(IslandTravelListing.describeLastActive(island.getLastActiveEpochMillis(), now), EnhancedDialog.VALUE_GRAY)));
        }

        DetailListScreen.Geometry geometry = DetailListScreen.TRAVEL;
        MasuGui gui = DetailListScreen.frame("island_travel", geometry, "Travel", EnhancedGuiHelper.countOf(destinations.size(), "public island", "public islands"),
                new ItemStack(Items.ENDER_PEARL), null,
                List.of(new KeyHint("Click", "an island for details"), new KeyHint("Enter", ": search")),
                EnhancedGuiHelper.countOf(destinations.size(), "island", "islands"));
        gui.add(new TextField("search", 5, 22, 118, 17).modern(true).searchIcon(true).maxLength(16)
                .placeholder("Search owner").value(query)
                .onSubmit((p, text) -> {
                    CompoundTag filtered = data.copy();
                    filtered.putString(QUERY_KEY, text.trim());
                    open(p, filtered);
                }).enhancedOnly());
        gui.add(new Dropdown("sort", 129, 22, 130, 17).modern(true).label(new TextComponent("Sort"))
                .options(Arrays.stream(Sort.values()).map(value -> (Component) new TextComponent(value.label)).toList())
                .selectedIndex(sort.ordinal())
                .onSelect((p, index) -> {
                    CompoundTag resorted = data.copy();
                    resorted.putInt(SORT_KEY, Sort.fromIndex(index).ordinal());
                    open(p, resorted);
                }).fallbackSlot(47));
        DetailListScreen.table(gui, geometry, "islands", "Island", rows,
                        List.of(ListColumn.of("Members", 60), ListColumn.of("Active", 8)), selected)
                .emptyText(new TextComponent(query.isEmpty() ? "No public islands yet" : "No island owner matches \"" + query + "\""))
                .fallbackSearch(true)
                .onClick((p, index, click) -> {
                    if (index < 0 || index >= destinations.size()) return;
                    CompoundTag selection = data.copy();
                    selection.putUUID(SELECTED_KEY, destinations.get(index).getId());
                    open(p, selection);
                });
        DetailListScreen.detail(gui, geometry, selected < 0 ? null : detail(server, destinations.get(selected), ownerNameOf, now),
                "No island to show");
        gui.openFor(player);
    }

    private static List<Island> sorted(List<Island> mostRecentlyActiveFirst, Sort sort, Function<Island, String> ownerNameOf) {
        List<Island> islands = new ArrayList<>(mostRecentlyActiveFirst);
        switch (sort) {
            case MOST_MEMBERS -> islands.sort(Comparator.comparingInt(IslandTravelListing::playerCount).reversed());
            case NAME -> islands.sort(Comparator.comparing(island -> ownerNameOf.apply(island).toLowerCase(Locale.ROOT)));
            case RECENTLY_ACTIVE -> {
            }
        }
        return islands;
    }

    private static String islandName(Island island, Function<Island, String> ownerNameOf) {
        return ownerNameOf.apply(island) + "'s Island";
    }

    private static DetailListScreen.Detail detail(MinecraftServer server, Island island, Function<Island, String> ownerNameOf, long now) {
        UUID islandId = island.getId();
        List<KeyValueRow> facts = List.of(
                KeyValueRow.of("Members", String.valueOf(IslandTravelListing.playerCount(island)), EnhancedDialog.VALUE_WHITE),
                KeyValueRow.of("Last active", IslandTravelListing.describeLastActive(island.getLastActiveEpochMillis(), now), EnhancedDialog.VALUE_WHITE),
                KeyValueRow.of("Biome", EnhancedGuiHelper.biomeName(island.getBiome()), EnhancedDialog.VALUE_WHITE),
                KeyValueRow.of("Visitors here", String.valueOf(((ForgeIsland) island).visitorsStandingOn(server).size()), EnhancedDialog.VALUE_WHITE));
        return new DetailListScreen.Detail(EnhancedGuiHelper.getPlayerHead(island.getOwner()), List.of(), islandName(island, ownerNameOf),
                facts, List.of(), DetailListScreen.DetailButton.of("teleport", "Teleport", ButtonStyle.PRIMARY, p -> travelTo(p, islandId)));
    }

    private static void travelTo(ServerPlayer player, UUID islandId) {
        MasuGui.closeFor(player);
        ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(islandId);
        if (island == null) return;
        if (IslandActionService.isBannedFrom(island, player)) {
            player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.banned.travel"))
                    .withStyle(ChatFormatting.RED), player.getUUID());
            return;
        }
        if (!island.isVisible() && !island.isPartOf(player.getUUID()) && !player.hasPermissions(Commands.LEVEL_MODERATORS)) {
            player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.travel.private"))
                    .withStyle(ChatFormatting.RED), player.getUUID());
            return;
        }
        island.teleportTo(player);
    }
}
