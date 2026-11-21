package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.ListView;
import com.masuary.masugui.element.Section;
import com.masuary.masugui.element.StatusBar;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.components.ItemStackComponent;
import yorickbm.skyblockaddon.core.registries.BiomeRegistry;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;
import yorickbm.skyblockaddon.util.IslandGroupAssignments;

import java.util.*;
import java.util.stream.Collectors;

/** The island lists (travel, members, biomes, groups, group members, assign group) as named rows with a back button. */
public final class ScrollableListGui {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 214;
    private static final int LIST_TOP = 34;

    private ScrollableListGui() {}

    public static void open(ServerPlayer player, CompoundTag data, String variant) {
        if ("groups".equals(variant) || "set_group".equals(variant)) {
            openGroupsLayout(player, data, variant);
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
                .emptyText(new TextComponent("Nothing here yet"))
                .onClick((p, index, click) -> handleItemClick(p, data, variant, index, items)));
        gui.openFor(player);
    }

    private static void openGroupsLayout(ServerPlayer player, CompoundTag data, String variant) {
        if (!data.contains("island_id")) return;
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
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
            if (item.getOrCreateTagElement("skyblockaddon").contains("group_id") && !lore.isEmpty()) {
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
            case "travel" -> {
                if (itemData.contains("island_id")) {
                    MasuGui.closeFor(player);
                    ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                            .getIslandByUUID(itemData.getUUID("island_id"));
                    if (island != null) island.teleportTo(player);
                }
            }
            case "members" -> {
                if (itemData.contains("player_id")) {
                    CompoundTag newData = data.copy();
                    newData.putUUID("player_id", itemData.getUUID("player_id"));
                    MasuGui.closeFor(player);
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:set_group", newData);
                }
            }
            case "biomes" -> {
                if (itemData.contains("biome")) {
                    MasuGui.closeFor(player);
                    ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                            .getIslandByUUID(data.getUUID("island_id"));
                    if (island != null) {
                        island.updateBiome(itemData.getString("biome"), player.getLevel());
                        player.sendMessage(new TextComponent("Biome updated to " + itemData.getString("biome"))
                                .withStyle(ChatFormatting.GREEN), player.getUUID());
                    }
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:biomes", data);
                }
            }
            case "groups" -> {
                if (itemData.contains("group_id")) {
                    CompoundTag newData = data.copy();
                    newData.putUUID("group_id", itemData.getUUID("group_id"));
                    MasuGui.closeFor(player);
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:permissions", newData);
                }
            }
            case "members_group" -> {
                if (itemData.contains("player_id")) {
                    CompoundTag newData = data.copy();
                    newData.putUUID("player_id", itemData.getUUID("player_id"));
                    MasuGui.closeFor(player);
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:set_group", newData);
                }
            }
            case "set_group" -> {
                if (itemData.contains("group_id") && data.contains("player_id")) {
                    MasuGui.closeFor(player);
                    ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                            .getIslandByUUID(data.getUUID("island_id"));
                    if (island != null) {
                        IslandGroup targetGroup = island.getGroup(itemData.getUUID("group_id"));
                        if (targetGroup != null) {
                            UUID playerId = data.getUUID("player_id");
                            boolean assigned = island.isPartOf(playerId)
                                    ? island.addMember(playerId, targetGroup.getId())
                                    : IslandGroupAssignments.assignWithoutMembership(island, playerId, targetGroup);
                            if (assigned) {
                                player.sendMessage(new TextComponent("Player assigned to group: " + targetGroup.getName())
                                        .withStyle(ChatFormatting.GREEN), player.getUUID());
                            }
                        }
                    }
                    GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:members", data);
                }
            }
        }
    }

    private static String resolveTitle(CompoundTag data, String variant) {
        if (data.contains("island_id")) {
            Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
            if (island != null) {
                String owner = UsernameCache.getBlocking(island.getOwner());
                return switch (variant) {
                    case "travel" -> "Travel Map";
                    case "members" -> owner + "'s Island - Members";
                    case "biomes" -> owner + "'s Island - Biomes";
                    case "groups" -> owner + "'s Island - Groups";
                    case "members_group" -> {
                        if (data.contains("group_id")) {
                            IslandGroup group = island.getGroup(data.getUUID("group_id"));
                            yield group != null ? group.getName() + " - Members" : "Group Members";
                        }
                        yield "Group Members";
                    }
                    case "set_group" -> owner + "'s Island - Assign Group";
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
            default -> null;
        };
    }

    private static List<ItemStack> buildItems(ServerPlayer player, CompoundTag data, String variant) {
        return switch (variant) {
            case "travel" -> buildTravelItems(player);
            case "members" -> buildMemberItems(data);
            case "biomes" -> buildBiomeItems(player, data);
            case "members_group" -> buildGroupMemberItems(data);
            default -> List.of();
        };
    }

    private static List<ItemStack> buildTravelItems(ServerPlayer player) {
        return IslandManager.getInstance().getIslands().stream()
                .filter(Island::isVisible)
                .map(island -> {
                    String ownerName = UsernameCache.getBlocking(island.getOwner());
                    ItemStack head = EnhancedGuiHelper.getPlayerHead(island.getOwner());
                    head.setHoverName(new TextComponent(ownerName + "'s Island").withStyle(ChatFormatting.GOLD));
                    CompoundTag tag = head.getOrCreateTagElement("skyblockaddon");
                    tag.putUUID("island_id", island.getId());
                    return head;
                })
                .collect(Collectors.toList());
    }

    private static List<ItemStack> buildMemberItems(CompoundTag data) {
        if (!data.contains("island_id")) return List.of();
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
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
                    tag.putUUID("player_id", uuid);
                    return head;
                })
                .collect(Collectors.toList());
    }

    private static List<ItemStack> buildBiomeItems(ServerPlayer player, CompoundTag data) {
        String currentBiome = "";
        if (data.contains("island_id")) {
            Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
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

            String biomeName = (String) component.getObject("biome", String.class);
            String displayName = (String) component.getObject("name", String.class);

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
            if (!groupId.equals(SkyblockAddonCore.MOD_UUID) && !groupId.equals(SkyblockAddonCore.MOD_UUID2)) continue;
            items.add(buildGroupItemStack(group));
        }
        return items;
    }

    private static List<ItemStack> buildCustomGroupItems(Island island) {
        List<ItemStack> items = new ArrayList<>();
        for (IslandGroup group : island.getGroups()) {
            UUID groupId = group.getId();
            if (groupId.equals(SkyblockAddonCore.MOD_UUID) || groupId.equals(SkyblockAddonCore.MOD_UUID2)) continue;
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
        tag.putUUID("group_id", group.getId());
        return item;
    }

    private static List<ItemStack> buildGroupMemberItems(CompoundTag data) {
        if (!data.contains("island_id") || !data.contains("group_id")) return List.of();
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return List.of();
        IslandGroup group = island.getGroup(data.getUUID("group_id"));
        if (group == null) return List.of();

        return group.getMembers().stream()
                .map(uuid -> {
                    String name = UsernameCache.getBlocking(uuid);
                    ItemStack head = EnhancedGuiHelper.getPlayerHead(uuid);
                    head.setHoverName(new TextComponent(name).withStyle(ChatFormatting.GREEN));
                    CompoundTag tag = head.getOrCreateTagElement("skyblockaddon");
                    tag.putUUID("player_id", uuid);
                    return head;
                })
                .collect(Collectors.toList());
    }
}
