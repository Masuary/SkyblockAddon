package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.ListView;
import com.masuary.masugui.element.Section;
import com.masuary.masugui.element.StatusBar;
import com.masuary.masugui.element.TextField;
import com.masuary.masugui.element.Window;
import com.masuary.masugui.element.data.Cell;
import com.masuary.masugui.element.data.Chip;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.ListColumn;
import com.masuary.masugui.element.data.ListRow;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.IslandTravelListing;
import yorickbm.skyblockaddon.components.ItemStackComponent;
import yorickbm.skyblockaddon.core.registries.BiomeRegistry;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.*;
import java.util.stream.Collectors;

/** The island lists (travel, members, biomes, groups, group members, assign group, visitors, bans, transfer) as named rows with a back button. */
public final class ScrollableListGui {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 214;
    private static final int LIST_TOP = 34;
    private static final String TRAVEL_QUERY_KEY = "travel_query";

    private ScrollableListGui() {}

    public static void open(ServerPlayer player, CompoundTag data, String variant) {
        if ("groups".equals(variant) || "set_group".equals(variant)) {
            openGroupsLayout(player, data, variant);
            return;
        }
        if ("travel".equals(variant)) {
            openTravelList(player, data);
            return;
        }
        openGenericList(player, data, variant);
    }

    private static MasuGui frame(String variant, CompoundTag data, int count) {
        String title = resolveTitle(data, variant);
        String backTarget = resolveBackTarget(variant);
        MasuGui gui = MasuGui.create("scrollable_list_" + variant)
                .title(new TextComponent(title))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_6);
        Window window = new Window("window", WIDTH, HEIGHT).title(new TextComponent(title)).accent(EnhancedDialog.ACCENT)
                .chip(Chip.of("", String.valueOf(count), 0xFFFFFF));
        if (backTarget != null) window.onBack(p -> GUILibraryRegistry.openGUIForPlayer(p, backTarget, data));
        gui.add(window);
        String subtitle = resolveSubtitle(data, variant);
        gui.add(new StatusBar("status", 1, HEIGHT - 13, WIDTH - 2)
                .hints(List.of(new KeyHint("Click", subtitle != null ? subtitle.replace("Click ", "") : "Select"))));
        return gui;
    }

    private static void openGenericList(ServerPlayer player, CompoundTag data, String variant) {
        List<ItemStack> items = buildItems(player, data, variant);
        MasuGui gui = frame(variant, data, items.size());
        gui.add(new Section("list_header", 1, 21, WIDTH - 2).label(new TextComponent(resolveTitle(data, variant))));
        gui.add(new ListView("list", 1, LIST_TOP, WIDTH - 2, HEIGHT - LIST_TOP - 14).rows(rows(items))
                .interactive(!"members".equals(variant) || canManage(player, data))
                .emptyText(new TextComponent("Nothing here yet"))
                .onClick((p, index, click) -> handleItemClick(p, data, variant, index, items)));
        gui.openFor(player);
    }

    /** Public islands, most recently active first, with their player count and last activity; Enter in the field filters by owner name. */
    private static void openTravelList(ServerPlayer player, CompoundTag data) {
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        String query = data.getString(TRAVEL_QUERY_KEY);
        List<Island> destinations = IslandTravelListing.travelDestinations(IslandManager.getInstance().getIslands(),
                player.getUUID(), query, island -> PlayerNames.nameOf(server, island.getOwner()));
        long now = System.currentTimeMillis();

        List<ListRow> rows = new ArrayList<>();
        for (Island island : destinations) {
            ItemStack head = EnhancedGuiHelper.getPlayerHead(island.getOwner());
            rows.add(ListRow.of(head, PlayerNames.nameOf(server, island.getOwner()) + "'s Island")
                    .withCells(Cell.of(String.valueOf(IslandTravelListing.playerCount(island)), 0xFFFFFF),
                            Cell.of(IslandTravelListing.describeLastActive(island.getLastActiveEpochMillis(), now), 0xAAAAAA)));
        }

        MasuGui gui = frame("travel", data, destinations.size());
        gui.add(new TextField("search", 4, 21, 140, 12).modern(true).searchIcon(true).maxLength(16)
                .placeholder("Search owner").value(query)
                .onSubmit((p, text) -> {
                    CompoundTag filtered = data.copy();
                    filtered.putString(TRAVEL_QUERY_KEY, text.trim());
                    open(p, filtered, "travel");
                }));
        gui.add(new ListView("list", 1, LIST_TOP, WIDTH - 2, HEIGHT - LIST_TOP - 14).rows(rows)
                .columns(List.of(ListColumn.of("Members", 96), ListColumn.of("Last active", 8)))
                .emptyText(new TextComponent(query.isEmpty() ? "No public islands yet" : "No island owner matches \"" + query + "\""))
                .onClick((p, index, click) -> {
                    if (index >= 0 && index < destinations.size()) travelTo(p, destinations.get(index).getId());
                }));
        gui.openFor(player);
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
        island.teleportTo(player);
    }

    private static boolean canManage(ServerPlayer player, CompoundTag data) {
        Island island = islandOf(data);
        return island != null && IslandActionService.canManage(island, player);
    }

    private static Island islandOf(CompoundTag data) {
        if (!data.hasUUID(GuiDataKeys.ISLAND_ID)) return null;
        return IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
    }

    private static void openGroupsLayout(ServerPlayer player, CompoundTag data, String variant) {
        if (!data.contains(GuiDataKeys.ISLAND_ID)) return;
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;

        List<ItemStack> defaultItems = buildDefaultGroupItems(island);
        List<ItemStack> customItems = buildCustomGroupItems(island);
        MasuGui gui = frame(variant, data, defaultItems.size() + customItems.size());

        int defaultHeight = Math.max(1, defaultItems.size()) * 18;
        gui.add(new Section("default_header", 1, 21, WIDTH - 2).label(new TextComponent("Default groups"))
                .right(new TextComponent("Members")));
        gui.add(new ListView("default_groups", 1, LIST_TOP, WIDTH - 2, defaultHeight).rows(rows(defaultItems))
                .columns(List.of(ListColumn.of("", 8)))
                .onClick((p, index, click) -> handleItemClick(p, data, variant, index, defaultItems)));

        int customTop = LIST_TOP + defaultHeight + 2;
        int bottom = "groups".equals(variant) ? HEIGHT - 36 : HEIGHT - 14;
        gui.add(new Section("custom_header", 1, customTop, WIDTH - 2).label(new TextComponent("Custom groups"))
                .right(new TextComponent("Members")));
        gui.add(new ListView("custom_groups", 1, customTop + 13, WIDTH - 2, Math.max(18, bottom - customTop - 13)).rows(rows(customItems))
                .columns(List.of(ListColumn.of("", 8)))
                .emptyText(new TextComponent("No custom groups"))
                .onClick((p, index, click) -> handleItemClick(p, data, variant, index, customItems)));

        if ("groups".equals(variant)) {
            gui.add(new Button("create_group", WIDTH - 104, HEIGHT - 33, 96, 15).style(ButtonStyle.ACCENT)
                    .label(new TextComponent("+ Create group"))
                    .onClick(p -> ConfirmCreateGroupGui.open(p, data)));
        }
        gui.openFor(player);
    }

    /** A row per item: its icon and name, its lore as the tooltip, and a member count for group items. */
    private static List<ListRow> rows(List<ItemStack> items) {
        List<ListRow> rows = new ArrayList<>();
        for (ItemStack item : items) {
            List<Component> lore = EnhancedGuiHelper.lore(item);
            ListRow row = ListRow.of(item.copy(), item.getHoverName().getString()).withTooltip(lore);
            if (item.getOrCreateTagElement("skyblockaddon").contains(GuiDataKeys.GROUP_ID) && !lore.isEmpty()) {
                row = row.withCells(Cell.of(lore.get(0).getString().replace(" members", ""), 0xAAAAAA));
            }
            rows.add(row);
        }
        return rows;
    }

    private static void handleItemClick(ServerPlayer player, CompoundTag data, String variant,
                                         int index, List<ItemStack> items) {
        if (index < 0 || index >= items.size()) return;
        ItemStack clicked = items.get(index);
        CompoundTag itemData = clicked.getOrCreateTagElement("skyblockaddon");

        switch (variant) {
            case "members" -> {
                if (itemData.contains(GuiDataKeys.PLAYER_ID)) {
                    CompoundTag newData = data.copy();
                    newData.putUUID(GuiDataKeys.PLAYER_ID, itemData.getUUID(GuiDataKeys.PLAYER_ID));
                    MasuGui.closeFor(player);
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:set_group", newData);
                }
            }
            case "biomes" -> {
                if (itemData.contains("biome")) {
                    if (EnhancedGuiHelper.closeWhenDenied(player, canManage(player, data), IslandActionResult.NOT_ALLOWED)) return;
                    MasuGui.closeFor(player);
                    ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                            .getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
                    if (island != null) {
                        island.updateBiome(itemData.getString("biome"), player.server);
                        player.sendMessage(new TextComponent("Biome updated to " + itemData.getString("biome"))
                                .withStyle(ChatFormatting.GREEN), player.getUUID());
                    }
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:biomes", data);
                }
            }
            case "groups" -> {
                if (itemData.contains(GuiDataKeys.GROUP_ID)) {
                    CompoundTag newData = data.copy();
                    newData.putUUID(GuiDataKeys.GROUP_ID, itemData.getUUID(GuiDataKeys.GROUP_ID));
                    MasuGui.closeFor(player);
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:permissions", newData);
                }
            }
            case "members_group" -> {
                if (itemData.contains(GuiDataKeys.PLAYER_ID)) {
                    CompoundTag newData = data.copy();
                    newData.putUUID(GuiDataKeys.PLAYER_ID, itemData.getUUID(GuiDataKeys.PLAYER_ID));
                    MasuGui.closeFor(player);
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:set_group", newData);
                }
            }
            case "set_group" -> {
                if (itemData.contains(GuiDataKeys.GROUP_ID) && data.contains(GuiDataKeys.PLAYER_ID)) {
                    MasuGui.closeFor(player);
                    Island island = islandOf(data);
                    if (island != null) {
                        UUID groupId = itemData.getUUID(GuiDataKeys.GROUP_ID);
                        UUID playerId = data.getUUID(GuiDataKeys.PLAYER_ID);
                        IslandActionResult result = IslandActionService.assignGroup(player, island, playerId, groupId);
                        if (result.succeeded()) {
                            player.sendMessage(new TextComponent("Player assigned to group: " + island.getGroup(groupId).getName())
                                    .withStyle(ChatFormatting.GREEN), player.getUUID());
                        } else {
                            IslandActionService.sendFailure(player, result, UsernameCache.getBlocking(playerId));
                        }
                    }
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:members", data);
                }
            }
            case "visitors", "bans", "transfer" -> {
                Island island = islandOf(data);
                if (island == null || !itemData.hasUUID(GuiDataKeys.PLAYER_ID)) return;
                UUID playerId = itemData.getUUID(GuiDataKeys.PLAYER_ID);
                switch (variant) {
                    case "visitors" -> VisitorActionGui.open(player, data, (ForgeIsland) island, playerId);
                    case "bans" -> ConfirmUnbanGui.open(player, data, island, playerId);
                    default -> ConfirmTransferGui.open(player, data, (ForgeIsland) island, playerId);
                }
            }
        }
    }

    private static String resolveTitle(CompoundTag data, String variant) {
        if (data.contains(GuiDataKeys.ISLAND_ID)) {
            Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
            if (island != null) {
                String owner = UsernameCache.getBlocking(island.getOwner());
                return switch (variant) {
                    case "travel" -> "Travel Map";
                    case "members" -> owner + "'s Island - Members";
                    case "biomes" -> owner + "'s Island - Biomes";
                    case "groups" -> owner + "'s Island - Groups";
                    case "members_group" -> {
                        if (data.contains(GuiDataKeys.GROUP_ID)) {
                            IslandGroup group = island.getGroup(data.getUUID(GuiDataKeys.GROUP_ID));
                            yield group != null ? group.getName() + " - Members" : "Group Members";
                        }
                        yield "Group Members";
                    }
                    case "set_group" -> owner + "'s Island - Assign Group";
                    case "visitors" -> owner + "'s Island - Visitors";
                    case "bans" -> owner + "'s Island - Banned players";
                    case "transfer" -> owner + "'s Island - Transfer ownership";
                    default -> "List";
                };
            }
        }
        if ("travel".equals(variant)) return "Travel Map";
        return "List";
    }

    private static String resolveSubtitle(CompoundTag data, String variant) {
        return switch (variant) {
            case "travel" -> "Click an island to travel there";
            case "members" -> "Click a member to manage";
            case "biomes" -> "Click a biome to apply";
            case "groups" -> "Click a group to edit permissions";
            case "members_group" -> "Click a member to reassign";
            case "set_group" -> "Click a group to assign the player";
            case "visitors" -> "Click a visitor to expel or ban them";
            case "bans" -> "Click a player to lift their ban";
            case "transfer" -> "Click the member who becomes the owner";
            default -> null;
        };
    }

    private static String resolveBackTarget(String variant) {
        return switch (variant) {
            case "travel" -> null;
            case "members" -> "skyblockaddon:overview";
            case "biomes" -> "skyblockaddon:settings";
            case "groups" -> "skyblockaddon:settings";
            case "members_group" -> "skyblockaddon:permissions";
            case "set_group" -> "skyblockaddon:members";
            case "visitors", "bans", "transfer" -> "skyblockaddon:overview";
            default -> null;
        };
    }

    private static List<ItemStack> buildItems(ServerPlayer player, CompoundTag data, String variant) {
        Island island = islandOf(data);
        return switch (variant) {
            case "members" -> buildMemberItems(data);
            case "biomes" -> buildBiomeItems(player, data);
            case "members_group" -> buildGroupMemberItems(data);
            case "visitors" -> island == null ? List.of() : buildPlayerItems(((ForgeIsland) island)
                    .visitorsStandingOn(Objects.requireNonNull(player.getServer())).stream().map(ServerPlayer::getUUID).toList(), "Visiting");
            case "bans" -> island == null ? List.of() : buildPlayerItems(island.getBannedPlayers(), "Banned");
            case "transfer" -> island == null ? List.of() : buildPlayerItems(island.getMembers(), "Member");
            default -> List.of();
        };
    }

    private static List<ItemStack> buildPlayerItems(List<UUID> players, String description) {
        return players.stream()
                .map(uuid -> {
                    ItemStack head = EnhancedGuiHelper.getPlayerHead(uuid);
                    head.setHoverName(new TextComponent(UsernameCache.getBlocking(uuid)).withStyle(ChatFormatting.GREEN));
                    EnhancedGuiHelper.addLore(head, new TextComponent(description).withStyle(ChatFormatting.GRAY));
                    head.getOrCreateTagElement("skyblockaddon").putUUID(GuiDataKeys.PLAYER_ID, uuid);
                    return head;
                })
                .collect(Collectors.toList());
    }

    private static List<ItemStack> buildMemberItems(CompoundTag data) {
        if (!data.contains(GuiDataKeys.ISLAND_ID)) return List.of();
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return List.of();

        return island.getMembers().stream()
                .map(uuid -> {
                    String name = UsernameCache.getBlocking(uuid);
                    ItemStack head = EnhancedGuiHelper.getPlayerHead(uuid);
                    head.setHoverName(new TextComponent(name).withStyle(ChatFormatting.GREEN));
                    String groupName = island.getGroupForEntityUUID(uuid)
                            .map(IslandGroup::getName)
                            .orElse("No Group");
                    EnhancedGuiHelper.addLore(head,
                            new TextComponent("Group: " + groupName).withStyle(ChatFormatting.GRAY));
                    CompoundTag tag = head.getOrCreateTagElement("skyblockaddon");
                    tag.putUUID(GuiDataKeys.PLAYER_ID, uuid);
                    return head;
                })
                .collect(Collectors.toList());
    }

    private static List<ItemStack> buildBiomeItems(ServerPlayer player, CompoundTag data) {
        String currentBiome = "";
        if (data.contains(GuiDataKeys.ISLAND_ID)) {
            Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
            if (island != null) currentBiome = island.getBiome();
        }

        BiomeRegistry biomeRegistry = new BiomeRegistry(
                FMLPaths.CONFIGDIR.get(),
                "minecraft:dead_bush",
                ForgeRegistries.BIOMES.getValues().stream()
                        .filter(b -> b.getRegistryName() != null)
                        .map(b -> b.getRegistryName().toString())
                        .toList()
        );

        String finalCurrentBiome = currentBiome;
        List<ItemStack> items = new ArrayList<>();

        while (biomeRegistry.hasNext()) {
            ItemStackComponent component = new ItemStackComponent();
            biomeRegistry.getNextData(component);

            String biomeName = component.getObject("biome", String.class);
            String displayName = component.getObject("name", String.class);

            Optional<String> iconItem = biomeRegistry.getDataForComponent(component);
            net.minecraft.world.item.Item itemType = Items.PAPER;
            if (iconItem.isPresent()) {
                net.minecraft.world.item.Item resolved = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(iconItem.get()));
                if (resolved != null && resolved != Items.AIR) {
                    itemType = resolved;
                }
            }

            boolean isCurrent = biomeName.equals(finalCurrentBiome);
            ItemStack item = new ItemStack(itemType);
            item.setHoverName(new TextComponent(displayName)
                    .withStyle(isCurrent ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            if (isCurrent) {
                EnhancedGuiHelper.addLore(item,
                        new TextComponent("Current biome").withStyle(ChatFormatting.GREEN));
            }
            for (TextComponent loreLine : yorickbm.skyblockaddon.util.BiomeLore.build(biomeName, player.getLevel())) {
                EnhancedGuiHelper.addLore(item, loreLine);
            }
            CompoundTag tag = item.getOrCreateTagElement("skyblockaddon");
            tag.putString("biome", biomeName);
            items.add(item);
        }

        return items;
    }

    private static List<ItemStack> buildDefaultGroupItems(Island island) {
        List<ItemStack> items = new ArrayList<>();
        for (IslandGroup group : island.getGroups()) {
            UUID groupId = group.getId();
            if (!groupId.equals(SkyblockAddonCore.MEMBERS_GROUP_ID) && !groupId.equals(SkyblockAddonCore.VISITORS_GROUP_ID)) continue;
            items.add(buildGroupItemStack(group));
        }
        return items;
    }

    private static List<ItemStack> buildCustomGroupItems(Island island) {
        List<ItemStack> items = new ArrayList<>();
        for (IslandGroup group : island.getGroups()) {
            UUID groupId = group.getId();
            if (groupId.equals(SkyblockAddonCore.MEMBERS_GROUP_ID) || groupId.equals(SkyblockAddonCore.VISITORS_GROUP_ID)) continue;
            items.add(buildGroupItemStack(group));
        }
        return items;
    }

    private static ItemStack buildGroupItemStack(IslandGroup group) {
        ItemStack item = ((ForgeIslandGroup) group).getItem().copy();
        item.setHoverName(new TextComponent(group.getName()).withStyle(ChatFormatting.AQUA));
        EnhancedGuiHelper.addLore(item,
                new TextComponent(group.getMembers().size() + " members").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = item.getOrCreateTagElement("skyblockaddon");
        tag.putUUID(GuiDataKeys.GROUP_ID, group.getId());
        return item;
    }

    private static List<ItemStack> buildGroupMemberItems(CompoundTag data) {
        if (!data.contains(GuiDataKeys.ISLAND_ID) || !data.contains(GuiDataKeys.GROUP_ID)) return List.of();
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return List.of();
        IslandGroup group = island.getGroup(data.getUUID(GuiDataKeys.GROUP_ID));
        if (group == null) return List.of();

        return group.getMembers().stream()
                .map(uuid -> {
                    String name = UsernameCache.getBlocking(uuid);
                    ItemStack head = EnhancedGuiHelper.getPlayerHead(uuid);
                    head.setHoverName(new TextComponent(name).withStyle(ChatFormatting.GREEN));
                    CompoundTag tag = head.getOrCreateTagElement("skyblockaddon");
                    tag.putUUID(GuiDataKeys.PLAYER_ID, uuid);
                    return head;
                })
                .collect(Collectors.toList());
    }
}
