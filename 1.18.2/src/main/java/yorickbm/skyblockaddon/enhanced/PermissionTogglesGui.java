package yorickbm.skyblockaddon.enhanced;

import com.google.gson.JsonParseException;
import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.ListView;
import com.masuary.masugui.element.Panel;
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
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.JSON.ItemStackJson;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.islands.IslandActionService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** A group's permissions: categories on the left, the category's rules on the right; a click toggles a rule. */
public final class PermissionTogglesGui {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int WIDTH = 440;
    private static final int HEIGHT = 234;
    private static final String CATEGORY_KEY = "permission_category";
    private static final String LEGACY_CATEGORY_KEY = "category_id";
    private static final String ADMIN_CATEGORY_ID = "admin_controls";
    private static final Set<String> MALFORMED_TEXT_WARNED = ConcurrentHashMap.newKeySet();

    record Category(String id, String label, Supplier<ItemStack> icon) {
    }

    private static final List<Category> KNOWN_CATEGORIES = List.of(
            new Category("general", "General", () -> new ItemStack(Items.GRASS_BLOCK)),
            new Category("building", "Building", () -> new ItemStack(Items.BRICKS)),
            new Category("storage", "Storage", () -> new ItemStack(Items.CHEST)),
            new Category("stations", "Stations", () -> new ItemStack(Items.CRAFTING_TABLE)),
            new Category("animals", "Animals and Mobs", () -> new ItemStack(Items.LEAD)),
            new Category("redstone", "Doors and Redstone", () -> new ItemStack(Items.REDSTONE)),
            new Category("transport", "Travel", () -> new ItemStack(Items.MINECART)),
            new Category("vaulthunters", "Vault Hunters", () -> new ItemStack(itemOrFallback("the_vault:vault_crystal", Items.ENDER_EYE))),
            new Category("mods", "Mods", () -> new ItemStack(Items.PISTON)));
    private static final Category ADMIN_CATEGORY = new Category(ADMIN_CATEGORY_ID, "Admin", () -> new ItemStack(Items.COMMAND_BLOCK));

    private PermissionTogglesGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains(GuiDataKeys.ISLAND_ID) || !data.contains(GuiDataKeys.GROUP_ID)) return;
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;
        IslandGroup group = island.getGroup(data.getUUID(GuiDataKeys.GROUP_ID));
        if (group == null) return;
        String categoryId = data.contains(CATEGORY_KEY)
                ? data.getString(CATEGORY_KEY)
                : data.getCompound(GUILibraryRegistry.MOD_ID).getString(LEGACY_CATEGORY_KEY);
        open(player, data, categoryId, group);
    }

    /**
     * The categories the registry has permissions in, in the fixed order; categories the order does not know follow
     * under their own id, and Admin comes last for server operators only.
     */
    static List<Category> categoriesFor(ServerPlayer player) {
        Set<String> registryCategories = PermissionManager.getInstance().getPermissions().stream()
                .map(permission -> permission.getCategory().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Category> categories = new ArrayList<>();
        for (Category category : KNOWN_CATEGORIES) {
            if (registryCategories.remove(category.id())) categories.add(category);
        }
        boolean hasAdminRules = registryCategories.remove(ADMIN_CATEGORY_ID);
        for (String unknownCategory : registryCategories) {
            categories.add(new Category(unknownCategory, prettify(unknownCategory), () -> new ItemStack(Items.WRITABLE_BOOK)));
        }
        if (hasAdminRules && player.hasPermissions(Commands.LEVEL_GAMEMASTERS)) categories.add(ADMIN_CATEGORY);
        return categories;
    }

    public static void open(ServerPlayer player, CompoundTag data, String requestedCategoryId, IslandGroup group) {
        List<Category> categories = categoriesFor(player);
        int active = 0;
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).id().equalsIgnoreCase(requestedCategoryId)) active = index;
        }
        Category category = categories.isEmpty() ? null : categories.get(active);
        String categoryId = category == null ? "" : category.id();
        List<Permission> permissions = category == null ? List.of() : PermissionManager.getInstance().getPermissionsFor(categoryId);
        long allowed = permissions.stream().filter(permission -> group.canDo(permission.getId())).count();

        MasuGui gui = MasuGui.create("permission_toggles")
                .title(new TextComponent("Permissions: " + group.getName()))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_6);
        gui.add(new Window("window", WIDTH, HEIGHT).title(new TextComponent("Permissions"))
                .subtitle(new TextComponent("Group: " + group.getName())).accent(EnhancedDialog.ACCENT)
                .fallbackIcon(new ItemStack(Items.WRITABLE_BOOK))
                .onBack(p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data)));

        addCategorySidebar(gui, data, group, categories, active);
        addToolbar(gui, data, categoryId, group, permissions);

        gui.add(new Panel("rules_card", 167, 43, 272, 176).style(Panel.Style.CARD));
        gui.add(new Section("rules_header", 169, 47, 268).label(new TextComponent(category == null ? "No rules" : category.label()))
                .right(new TextComponent(allowed + " of " + permissions.size() + " allowed")));
        List<ListRow> rows = new ArrayList<>();
        for (Permission permission : permissions) {
            boolean enabled = group.canDo(permission.getId());
            rows.add(ListRow.of(ruleIcon(permission), displayName(permission))
                    .withSwitch(enabled)
                    .withCells(Cell.of(enabled ? "Allowed" : "Denied", enabled ? EnhancedDialog.VALUE_GREEN : EnhancedDialog.VALUE_RED))
                    .withTooltip(tooltip(permission, group, enabled)));
        }
        gui.add(new ListView("rules", 169, 60, 267, 155).rowHeight(DetailListScreen.ROW_HEIGHT).rows(rows)
                .columns(List.of(ListColumn.of("", 8)))
                .emptyText(new TextComponent("No rules in this category"))
                .fallbackHints(List.of(new KeyHint("Click", "to allow or deny")))
                .onClick((p, index, click) -> {
                    if (index < 0 || index >= permissions.size()) return;
                    if (EnhancedGuiHelper.closeWhenDenied(p, mayChangePermissions(p, data, categoryId), IslandActionResult.NOT_ALLOWED)) return;
                    Permission permission = permissions.get(index);
                    group.setPermission(permission.getId(), !group.canDo(permission.getId()));
                    open(p, data, categoryId, group);
                }));

        gui.add(new StatusBar("status", 1, HEIGHT - 13, WIDTH - 2)
                .hints(List.of(new KeyHint("Click", "a rule to allow or deny it"), new KeyHint("Hover", "for what it covers")))
                .right(new TextComponent("Saved instantly")));
        gui.openFor(player);
    }

    private static void addCategorySidebar(MasuGui gui, CompoundTag data, IslandGroup group, List<Category> categories, int active) {
        List<SidebarEntry> entries = new ArrayList<>();
        for (Category category : categories) {
            List<Permission> categoryPermissions = PermissionManager.getInstance().getPermissionsFor(category.id());
            long categoryAllowed = categoryPermissions.stream().filter(permission -> group.canDo(permission.getId())).count();
            entries.add(SidebarEntry.of(category.label(), category.icon().get(), categoryAllowed + "/" + categoryPermissions.size()));
        }
        gui.add(new Panel("category_card", 1, 21, 162, 198).style(Panel.Style.CARD));
        gui.add(new Sidebar("category", 3, 25, 158, 190).rowHeight(DetailListScreen.ROW_HEIGHT).entries(entries).active(active)
                .onSelect((p, index, click) -> {
                    if (index >= 0 && index < categories.size()) open(p, data, categories.get(index).id(), group);
                }));
    }

    private static void addToolbar(MasuGui gui, CompoundTag data, String categoryId, IslandGroup group, List<Permission> permissions) {
        gui.add(new Button("allow_all", 167, 22, 66, 17).style(ButtonStyle.SECONDARY).label(new TextComponent("Allow all"))
                .onClick(p -> setAll(p, data, categoryId, group, permissions, true)).fallbackSlot(47));
        gui.add(new Button("deny_all", 237, 22, 66, 17).style(ButtonStyle.SECONDARY).label(new TextComponent("Deny all"))
                .onClick(p -> setAll(p, data, categoryId, group, permissions, false)).fallbackSlot(51));
        CompoundTag playersData = data.copy();
        playersData.putString(CATEGORY_KEY, categoryId);
        playersData.remove(GroupAssignGui.PLAYERS_BACK_TO_GROUPS_KEY);
        gui.add(new Button("players", 369, 22, 68, 17).style(ButtonStyle.SECONDARY).label(new TextComponent("Players"))
                .onClick(p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members_group", playersData)).fallbackSlot(52));
    }

    private static void setAll(ServerPlayer player, CompoundTag data, String categoryId, IslandGroup group,
                               List<Permission> permissions, boolean allowed) {
        if (EnhancedGuiHelper.closeWhenDenied(player, mayChangePermissions(player, data, categoryId), IslandActionResult.NOT_ALLOWED)) return;
        for (Permission permission : permissions) group.setPermission(permission.getId(), allowed);
        open(player, data, categoryId, group);
    }

    private static boolean mayChangePermissions(ServerPlayer player, CompoundTag data, String categoryId) {
        if (ADMIN_CATEGORY_ID.equals(categoryId) && !player.hasPermissions(Commands.LEVEL_GAMEMASTERS)) return false;
        Island island = data.hasUUID(GuiDataKeys.ISLAND_ID) ? IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID)) : null;
        return island != null && IslandActionService.canManage(island, player);
    }

    private static ItemStack ruleIcon(Permission permission) {
        ItemStackJson itemJson = permission.getItem();
        if (itemJson == null || itemJson.getItem() == null) return new ItemStack(Items.BARRIER);
        return new ItemStack(itemOrFallback(itemJson.getItem(), Items.BARRIER));
    }

    private static Item itemOrFallback(String itemId, Item fallback) {
        ResourceLocation location = ResourceLocation.tryParse(itemId.toLowerCase(Locale.ROOT));
        if (location == null) return fallback;
        Item item = ForgeRegistries.ITEMS.getValue(location);
        return item == null || item == Items.AIR ? fallback : item;
    }

    private static String displayName(Permission permission) {
        ItemStackJson itemJson = permission.getItem();
        if (itemJson != null && itemJson.getDisplay_name() != null) {
            String name = jsonLine(permission, itemJson.getDisplay_name(), Map.of()).getString().trim();
            if (!name.isEmpty()) return name;
        }
        return prettify(permission.getId());
    }

    /** The rule's name, then its lore up to the first blank line (description and examples), placeholders filled in. */
    private static List<Component> tooltip(Permission permission, IslandGroup group, boolean enabled) {
        ItemStackJson itemJson = permission.getItem();
        List<Component> lines = new ArrayList<>();
        if (itemJson == null) return lines;
        Map<String, String> placeholders = Map.of(
                "%data_group_name%", group.getName(),
                "%data_status%", SkyBlockAddonLanguage.getLocalizedString(enabled ? "island.permission.enabled" : "island.permission.disabled"));
        if (itemJson.getDisplay_name() != null) {
            MutableComponent title = jsonLine(permission, itemJson.getDisplay_name(), placeholders);
            if (!title.getString().isBlank()) lines.add(title);
        }
        if (itemJson.getLore() == null) return lines;
        for (String[] loreLine : itemJson.getLore()) {
            MutableComponent line = jsonLine(permission, loreLine, placeholders);
            if (line.getString().isBlank()) break;
            lines.add(line);
        }
        return lines;
    }

    private static MutableComponent jsonLine(Permission permission, String[] jsonParts, Map<String, String> placeholders) {
        MutableComponent line = new TextComponent("");
        for (String jsonPart : jsonParts) {
            Component part = parseText(permission, jsonPart);
            String text = part.getString();
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                text = text.replace(placeholder.getKey(), placeholder.getValue());
            }
            line.append(new TextComponent(text).withStyle(part.getStyle()));
        }
        return line;
    }

    /** A malformed text in the permission registry shows as plain text and is reported once per permission. */
    private static Component parseText(Permission permission, String jsonPart) {
        try {
            Component parsed = Component.Serializer.fromJson(jsonPart);
            if (parsed != null) return parsed;
        } catch (JsonParseException exception) {
            if (MALFORMED_TEXT_WARNED.add(permission.getId())) {
                LOGGER.warn("Permission {} has a malformed text component in its item, shown as plain text: {}",
                        permission.getId(), exception.getMessage());
            }
        }
        return new TextComponent(jsonPart);
    }

    private static String prettify(String identifier) {
        if (identifier == null || identifier.isEmpty()) return "Unknown";
        String name = identifier.contains(".") ? identifier.substring(identifier.lastIndexOf('.') + 1) : identifier;
        return Arrays.stream(name.replace("_", " ").split(" "))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
