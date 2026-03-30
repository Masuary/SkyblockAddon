package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.*;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.Panel;
import com.masuary.masugui.fallback.FallbackType;
import com.masuary.masugui.session.GuiSessionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.*;
import java.util.stream.Collectors;

public final class ScrollableListGui {

    private static final int COLUMNS = 9;
    private static final int CELL_SIZE = 18;
    private static final int GUI_WIDTH = 220;
    private static final int GRID_X = (GUI_WIDTH - COLUMNS * CELL_SIZE) / 2;
    private static final int HEADER_HEIGHT = 30;
    private static final int NAV_HEIGHT = 18;
    private static final int MIN_HEIGHT = 140;
    private static final int MAX_HEIGHT = 260;

    private ScrollableListGui() {}

    public static void open(ServerPlayer player, CompoundTag data, String variant) {
        String title = resolveTitle(data, variant);
        String subtitle = resolveSubtitle(data, variant);
        List<ItemStack> items = buildItems(player, data, variant);
        String backTarget = resolveBackTarget(variant);

        int itemCount = items.size();
        int gridRows = Math.max(1, (itemCount + COLUMNS - 1) / COLUMNS);
        int gridHeight = gridRows * CELL_SIZE;

        int contentHeight = HEADER_HEIGHT + gridHeight + 4 + NAV_HEIGHT + 6;
        int totalHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, contentHeight));

        boolean needsScroll = contentHeight > MAX_HEIGHT;
        int displayRows = needsScroll
                ? Math.max(1, (MAX_HEIGHT - HEADER_HEIGHT - NAV_HEIGHT - 10) / CELL_SIZE)
                : gridRows;

        if (needsScroll) {
            totalHeight = MAX_HEIGHT;
        }

        int navY = totalHeight - NAV_HEIGHT - 4;
        int navDivY = navY - 4;

        MasuGui gui = MasuGui.create("scrollable_list_" + variant)
                .title(new TextComponent(title))
                .size(GUI_WIDTH, totalHeight)
                .fallbackType(FallbackType.CHEST_6);

        gui.add(new Panel("bg", 0, 0, GUI_WIDTH, totalHeight)
                .color(0xE8181818).border(0x333333));

        gui.add(new Button("close_btn", GUI_WIDTH - 16, 2, 12, 12)
                .label(new TextComponent("X")).backgroundColor(0xFFAA4444).flat()
                .onClick(MasuGui::closeFor));

        gui.add(new Label("title", GUI_WIDTH / 2, 6)
                .text(new TextComponent(title).withStyle(ChatFormatting.GOLD))
                .centered().scale(1.0f).shadow(true));

        if (subtitle != null) {
            gui.add(new Label("subtitle", GUI_WIDTH / 2, 18)
                    .text(new TextComponent(subtitle))
                    .color(0xFFAAAAAA).centered().scale(0.7f));
        }

        gui.add(new Divider("header_div", 8, HEADER_HEIGHT - 2, GUI_WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        ItemGrid grid = new ItemGrid("list_grid", GRID_X, HEADER_HEIGHT, COLUMNS, displayRows)
                .items(items)
                .hoverHighlight(0xFF55FFFF)
                .onClick((p, idx) -> {});
        if (needsScroll) {
            grid.clientSideScroll(true);
        }
        gui.add(grid);

        gui.add(new Divider("nav_div", 8, navDivY, GUI_WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        gui.add(new Button("back_btn", 10, navY, 32, 14)
                .label(new TextComponent("Back")).backgroundColor(0xFFAA4444).flat()
                .onClick(p -> {
                    MasuGui.closeFor(p);
                    if (backTarget != null) {
                        GUILibraryRegistry.openGUIForPlayer(p, backTarget, data);
                    }
                }));

        gui.openFor(player);

        GuiSessionManager.getSession(player.getUUID()).ifPresent(session ->
                session.registerIndexedButtonClickHandler("list_grid", (p, idx, click) ->
                        handleItemClick(p, data, variant, idx, items)));
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
                            island.getGroupForEntityUUID(data.getUUID("player_id"))
                                    .ifPresent(oldGroup -> oldGroup.removeMember(data.getUUID("player_id")));
                            targetGroup.addMember(data.getUUID("player_id"));
                            player.sendMessage(new TextComponent("Player assigned to group: " + targetGroup.getName())
                                    .withStyle(ChatFormatting.GREEN), player.getUUID());
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
            case "biomes" -> buildBiomeItems(data);
            case "groups" -> buildGroupItems(data);
            case "members_group" -> buildGroupMemberItems(data);
            case "set_group" -> buildGroupItems(data);
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

    private static List<ItemStack> buildBiomeItems(CompoundTag data) {
        String currentBiome = "";
        if (data.contains("island_id")) {
            Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
            if (island != null) currentBiome = island.getBiome();
        }

        String finalCurrentBiome = currentBiome;
        return ForgeRegistries.BIOMES.getValues().stream()
                .filter(b -> b.getRegistryName() != null && b.getRegistryName().toString().startsWith("minecraft:"))
                .map(biome -> {
                    String biomeName = biome.getRegistryName().toString();
                    String displayName = biomeName.replace("minecraft:", "")
                            .replace("_", " ");
                    displayName = Arrays.stream(displayName.split(" "))
                            .map(w -> w.substring(0, 1).toUpperCase() + w.substring(1))
                            .collect(Collectors.joining(" "));

                    boolean isCurrent = biomeName.equals(finalCurrentBiome);
                    ItemStack item = new ItemStack(isCurrent ? Items.LIME_DYE : Items.PAPER);
                    item.setHoverName(new TextComponent(displayName)
                            .withStyle(isCurrent ? ChatFormatting.GREEN : ChatFormatting.WHITE));
                    if (isCurrent) {
                        EnhancedGuiHelper.addLore(item,
                                new TextComponent("Current biome").withStyle(ChatFormatting.GREEN));
                    }
                    CompoundTag tag = item.getOrCreateTagElement("skyblockaddon");
                    tag.putString("biome", biomeName);
                    return item;
                })
                .collect(Collectors.toList());
    }

    private static List<ItemStack> buildGroupItems(CompoundTag data) {
        if (!data.contains("island_id")) return List.of();
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return List.of();

        return island.getGroups().stream()
                .map(group -> {
                    ItemStack item = new ItemStack(Items.BOOK);
                    item.setHoverName(new TextComponent(group.getName()).withStyle(ChatFormatting.AQUA));
                    EnhancedGuiHelper.addLore(item,
                            new TextComponent(group.getMembers().size() + " members").withStyle(ChatFormatting.GRAY));
                    CompoundTag tag = item.getOrCreateTagElement("skyblockaddon");
                    tag.putUUID("group_id", group.getId());
                    return item;
                })
                .collect(Collectors.toList());
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
