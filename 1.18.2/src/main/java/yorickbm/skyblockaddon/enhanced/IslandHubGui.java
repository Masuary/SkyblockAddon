package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.CardGrid;
import com.masuary.masugui.element.ItemDisplay;
import com.masuary.masugui.element.KeyValueList;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.Panel;
import com.masuary.masugui.element.Section;
import com.masuary.masugui.element.StatusBar;
import com.masuary.masugui.element.ToggleSwitch;
import com.masuary.masugui.element.Window;
import com.masuary.masugui.element.data.Card;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.KeyValueRow;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.IslandTravelListing;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The island menu: an island card (owner, facts, Teleport, Transfer and Leave) beside the "Manage island" cards the
 * viewer may use and, for managers, the visibility switch.
 */
public final class IslandHubGui {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 214;
    private static final int INFO_CENTER = 75;
    private static final int INFO_BUTTON_LEFT = 7;
    private static final int INFO_BUTTON_WIDTH = 136;

    private record Action(Card card, Consumer<ServerPlayer> run) {
    }

    private IslandHubGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains(GuiDataKeys.ISLAND_ID)) return;

        ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                .getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;

        MinecraftServer server = Objects.requireNonNull(player.getServer());
        String ownerName = UsernameCache.getBlocking(island.getOwner());
        String title = ownerName + "'s Island";
        boolean canManageIsland = IslandActionService.canManage(island, player);
        boolean isOwner = island.isOwner(player.getUUID());
        boolean isPart = island.isPartOf(player.getUUID());
        String biome = EnhancedGuiHelper.biomeName(island.getBiome());
        int visitorCount = island.visitorsStandingOn(server).size();
        List<KeyValueRow> facts = List.of(
                KeyValueRow.of("Biome", biome, EnhancedDialog.VALUE_WHITE),
                KeyValueRow.of("Visibility", island.isVisible() ? "Public" : "Private",
                        island.isVisible() ? EnhancedDialog.VALUE_GREEN : EnhancedDialog.VALUE_GRAY),
                KeyValueRow.of("Members", String.valueOf(IslandTravelListing.playerCount(island)), EnhancedDialog.VALUE_WHITE),
                KeyValueRow.of("Visitors here", String.valueOf(visitorCount), EnhancedDialog.VALUE_WHITE),
                KeyValueRow.of("Groups", String.valueOf(island.getGroups().size()), EnhancedDialog.VALUE_WHITE));

        MasuGui gui = MasuGui.create("island_hub")
                .title(new TextComponent(title))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_4);
        gui.add(new Window("window", WIDTH, HEIGHT).title(new TextComponent(title)).accent(EnhancedDialog.ACCENT)
                .fallbackIcon(EnhancedGuiHelper.getPlayerHead(island.getOwner()))
                .fallbackInfo(facts.stream().map(IslandHubGui::factLine).toList()));
        addIslandCard(gui, island, data, title, roleText(isOwner, isPart), facts, isOwner, isPart);
        addManageCards(gui, actions(player, island, data, canManageIsland, biome, visitorCount));
        if (canManageIsland) addVisibilitySwitch(gui, island, data);
        gui.add(new StatusBar("status", 1, HEIGHT - 13, WIDTH - 2).hints(List.of(new KeyHint("Click", "a card to open it")))
                .right(new TextComponent("Owner: " + ownerName)));
        gui.openFor(player);
    }

    private static String roleText(boolean isOwner, boolean isPart) {
        if (isOwner) return "You own this island";
        return isPart ? "You are a member" : "Visiting";
    }

    private static Component factLine(KeyValueRow row) {
        return new TextComponent(row.label().getString() + ": ").withStyle(style -> style.withColor(EnhancedDialog.VALUE_GRAY))
                .append(new TextComponent(row.value().getString()).withStyle(style -> style.withColor(row.color())));
    }

    private static void addIslandCard(MasuGui gui, ForgeIsland island, CompoundTag data, String title, String role,
                                      List<KeyValueRow> facts, boolean isOwner, boolean isPart) {
        gui.add(new Panel("info_card", 1, 21, 148, 178).style(Panel.Style.CARD));
        gui.add(new ItemDisplay("owner_head", INFO_CENTER - 16, 28, 32).item(EnhancedGuiHelper.getPlayerHead(island.getOwner()))
                .framed(true).enhancedOnly());
        gui.add(new Label("island_name", INFO_CENTER, 66).text(new TextComponent(title)).role(Label.Role.TITLE)
                .centered().maxWidth(140).enhancedOnly());
        gui.add(new Label("your_role", INFO_CENTER, 77).text(new TextComponent(role)).role(Label.Role.LABEL)
                .centered().maxWidth(140).enhancedOnly());
        gui.add(new KeyValueList("facts", 8, 90, 134).rowHeight(11).rows(facts));
        gui.add(new Button("teleport", INFO_BUTTON_LEFT, 150, INFO_BUTTON_WIDTH, 22).style(ButtonStyle.PRIMARY)
                .label(new TextComponent("Teleport")).onClick(p -> {
                    MasuGui.closeFor(p);
                    island.teleportTo(p);
                }).fallbackSlot(19));
        int halfWidth = (INFO_BUTTON_WIDTH - 4) / 2;
        if (isOwner) {
            gui.add(new Button("transfer", INFO_BUTTON_LEFT, 176, halfWidth, 17).style(ButtonStyle.SECONDARY)
                    .label(new TextComponent("Transfer"))
                    .onClick(p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members", data)).fallbackSlot(23));
        }
        if (isPart) {
            int leaveLeft = isOwner ? INFO_BUTTON_LEFT + halfWidth + 4 : INFO_BUTTON_LEFT;
            int leaveWidth = isOwner ? halfWidth : INFO_BUTTON_WIDTH;
            gui.add(new Button("leave", leaveLeft, 176, leaveWidth, 17).style(ButtonStyle.DANGER)
                    .label(new TextComponent("Leave")).onClick(p -> ConfirmLeaveGui.open(p, data)).fallbackSlot(25));
        }
    }

    private static List<Action> actions(ServerPlayer player, ForgeIsland island, CompoundTag data, boolean canManageIsland,
                                        String biome, int visitorCount) {
        List<Action> actions = new ArrayList<>();
        int memberCount = IslandTravelListing.playerCount(island);
        actions.add(new Action(Card.of(new ItemStack(Items.PLAYER_HEAD), "Members",
                memberCount + " members" + (canManageIsland ? ", set groups" : "")),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members", data)));
        if (canManageIsland) {
            actions.add(new Action(Card.of(new ItemStack(Items.SPYGLASS), "Visitors", visitorCount + " here now"),
                    p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:visitors", data)));
            actions.add(new Action(Card.of(new ItemStack(Items.WRITABLE_BOOK), "Permissions", island.getGroups().size() + " groups"),
                    p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data)));
            actions.add(new Action(Card.of(new ItemStack(Items.IRON_BARS), "Bans", island.getBannedPlayers().size() + " banned"),
                    p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:bans", data)));
            actions.add(new Action(Card.of(new ItemStack(Items.GRASS_BLOCK), "Biome", biome),
                    p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:biomes", data)));
        }
        if (IslandActionService.canSetSpawn(island, player)) {
            actions.add(new Action(Card.of(new ItemStack(Items.LODESTONE), "Set spawn", "Use your position"),
                    p -> ConfirmSetSpawnGui.open(p, data)));
        }
        return actions;
    }

    private static void addManageCards(MasuGui gui, List<Action> actions) {
        gui.add(new Section("manage_header", 153, 21, 246).label(new TextComponent("Manage island")));
        gui.add(new CardGrid("actions", 153, 34, 246, 141).columns(2).cardHeight(30).gap(3)
                .cards(actions.stream().map(Action::card).toList())
                .onClick((p, index, click) -> {
                    if (index >= 0 && index < actions.size()) actions.get(index).run().accept(p);
                }));
    }

    private static void addVisibilitySwitch(MasuGui gui, ForgeIsland island, CompoundTag data) {
        gui.add(new ToggleSwitch("visibility", 157, 182, 238).on(island.isVisible())
                .label(new TextComponent("Public island")).description(new TextComponent("Shown in /island travel"))
                .onToggle((p, wantsPublic) -> {
                    if (wantsPublic != island.isVisible()) {
                        IslandActionResult result = IslandActionService.toggleVisibility(p, island);
                        if (!result.succeeded()) IslandActionService.sendFailure(p, result, "");
                    }
                    open(p, data);
                }).fallbackSlot(21));
    }
}
