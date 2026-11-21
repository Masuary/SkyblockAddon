package yorickbm.skyblockaddon.events.Gui;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.guilibrary.events.GuiDrawItemEvent;
import yorickbm.guilibrary.events.OpenMenuEvent;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GuiEvents {
    private static final String GUI_PREFIX = SkyblockAddonCore.MOD_ID + ":";
    private static final Set<String> MANAGER_MENUS = Set.of(
            GUI_PREFIX + "settings", GUI_PREFIX + "biomes", GUI_PREFIX + "groups", GUI_PREFIX + "permissions",
            GUI_PREFIX + "set_permission", GUI_PREFIX + "members_group", GUI_PREFIX + "set_group",
            GUI_PREFIX + "visitors", GUI_PREFIX + "bans");
    private static final Set<String> OWNER_MENUS = Set.of(GUI_PREFIX + "transfer");

    @SubscribeEvent
    public void itemRenderer(final GuiDrawItemEvent event) {
        if(event.isCanceled()) return; //Skip if canceled

        //Ignore condition filter if no island_id is set
        if(!event.getHolder().getData().contains(GuiDataKeys.ISLAND_ID)) { return; }

        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(event.getHolder().getData().getUUID(GuiDataKeys.ISLAND_ID));

        //Ignore condition if not found
        if(island == null) { return; }

        final boolean isAdmin = event.getItemHolder().hasCondition("is_admin");
        final boolean isNotAdmin = event.getItemHolder().hasCondition("!is_admin");

        if (isAdmin || isNotAdmin) {
            final boolean canManage = IslandActionService.canManage(island, (ServerPlayer) event.getHolder().getOwner());
            event.setCanceled(isAdmin != canManage);
        }

        if (event.getItemHolder().hasCondition("is_owner")) {
            event.setCanceled(!island.isOwner(event.getHolder().getOwner().getUUID()));
        }

        if(event.getItemHolder().hasCondition("is_op")) {
            event.setCanceled(!event.getHolder().getOwner().hasPermissions(Commands.LEVEL_ADMINS));
        }

        if(event.getItemHolder().hasCondition("is_part")) {
            event.setCanceled(!island.isPartOf(event.getHolder().getOwner().getUUID()));
        } else if(event.getItemHolder().hasCondition("!is_part")) {
            event.setCanceled(island.isPartOf(event.getHolder().getOwner().getUUID()));
        }
    }

    /**
     * Menus that change the island open only for players allowed to use them, for vanilla and modded clients alike.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void guardRestrictedMenus(final OpenMenuEvent event) {
        final String guiId = event.getGuiId();
        final boolean managerMenu = MANAGER_MENUS.contains(guiId);
        final boolean ownerMenu = OWNER_MENUS.contains(guiId);
        if (!managerMenu && !ownerMenu) return;

        final ServerPlayer player = event.getTarget();
        final Island island = event.getData().hasUUID(GuiDataKeys.ISLAND_ID)
                ? IslandManager.getInstance().getIslandByUUID(event.getData().getUUID(GuiDataKeys.ISLAND_ID))
                : null;
        if (island == null) {
            event.setCanceled(true);
            return;
        }

        final boolean allowed = ownerMenu ? island.isOwner(player.getUUID()) : IslandActionService.canManage(island, player);
        if (allowed) return;

        event.setCanceled(true);
        IslandActionService.sendFailure(player, ownerMenu ? IslandActionResult.OWNER_ONLY : IslandActionResult.NOT_ALLOWED, "");
    }

    @SubscribeEvent
    public void dynamicTitle(final OpenMenuEvent event) {
        if(event.isCanceled()) return; //Skip if canceled

        //Ignore condition filter if no island_id is set
        if(!event.getData().contains(GuiDataKeys.ISLAND_ID)) { return; }

        final ForgeIsland island = (ForgeIsland)IslandManager.getInstance().getIslandByUUID(event.getData().getUUID(GuiDataKeys.ISLAND_ID));

        //Ignore condition if not found
        if (island == null) {
            return;
        }

        final List<TextComponent> title = event.getTitle();

        //Add island variables
        final Map<String, String> replacements = new HashMap<>() {{
            put("%owner%", UsernameCache.getBlocking(island.getOwner()));
            put("%x%", island.getSpawn().getX()+"");
            put("%y%", island.getSpawn().getY()+"");
            put("%z%", island.getSpawn().getZ()+"");
            put("%biome%", island.getBiome());
            put("%visibility%", island.isVisible() ? SkyBlockAddonLanguage.getLocalizedString("island.public") : SkyBlockAddonLanguage.getLocalizedString("island.private"));

        }};

        //Add group variables
        if(event.getData().contains(GuiDataKeys.GROUP_ID)) {
            final IslandGroup group = island.getGroup(event.getData().getUUID(GuiDataKeys.GROUP_ID));
            replacements.put("%group_name%", group.getName());
            replacements.put("%group_id%", group.getId().toString());
            replacements.put("%group_member_count%", group.getMembers().size()+"");

            if(event.getData().getCompound(GUILibraryRegistry.MOD_ID).contains("category_id")) {
                final String category = event.getData().getCompound(GUILibraryRegistry.MOD_ID).getString("category_id").replace("_", " ");
                replacements.put("%group_category%", Character.toUpperCase(category.charAt(0)) + category.substring(1));
            }

        }

        //Parse title through variables
        event.setTitle(title.stream()
                .map(component -> {
                    final String modifiedText = replacements.entrySet().stream()
                            .reduce(component.getString(), (text, entry) -> text.replace(entry.getKey(), entry.getValue()), (a, b) -> b);
                    return new TextComponent(modifiedText).withStyle(component.getStyle());
                })
                .filter(c -> c instanceof TextComponent) // Ensure it's a TextComponent
                .map(c -> (TextComponent) c) // Cast to TextComponent
                .collect(Collectors.toList()));
    }

    @SubscribeEvent
    public void dynamicText(final GuiDrawItemEvent event) {
        if(event.isCanceled()) return; //Skip if canceled

        //Ignore condition filter if no island_id is set
        if(!event.getHolder().getData().contains(GuiDataKeys.ISLAND_ID)) { return; }

        final ForgeIsland island = (ForgeIsland)IslandManager.getInstance().getIslandByUUID(event.getHolder().getData().getUUID(GuiDataKeys.ISLAND_ID));

        //Ignore condition if not found
        if(island == null) { return; }

        final List<TextComponent> name = event.getItemStackHolder().getDisplayName();
        final List<List<TextComponent>> lore = event.getItemStackHolder().getLore();

        final Map<String, String> replacements = new HashMap<>() {{
            put("%owner%", UsernameCache.getBlocking(island.getOwner()));
            put("%x%", island.getSpawn().getX()+"");
            put("%y%", island.getSpawn().getY()+"");
            put("%z%", island.getSpawn().getZ()+"");
            put("%biome%", island.getBiome());
            put("%visibility%", island.isVisible() ? SkyBlockAddonLanguage.getLocalizedString("island.public") : SkyBlockAddonLanguage.getLocalizedString("island.private"));
        }};

        if(event.getHolder().getData().contains(GuiDataKeys.GROUP_ID)) {
            final IslandGroup group = island.getGroup(event.getHolder().getData().getUUID(GuiDataKeys.GROUP_ID));
            replacements.put("%group_name%", group.getName());
            replacements.put("%group_id%", group.getId().toString());
            replacements.put("%group_member_count%", group.getMembers().size()+"");

            if(event.getHolder().getData().getCompound(GUILibraryRegistry.MOD_ID).contains("category_id")) {
                final String category = event.getHolder().getData().getCompound(GUILibraryRegistry.MOD_ID).getString("category_id").replace("_", " ");
                replacements.put("%group_category%", Character.toUpperCase(category.charAt(0)) + category.substring(1));
            }

        }

        event.getItemStackHolder().setDisplayName(name.stream()
                .map(component -> {
                    final String modifiedText = replacements.entrySet().stream()
                            .reduce(component.getString(), (text, entry) -> text.replace(entry.getKey(), entry.getValue()), (a, b) -> b);
                    return new TextComponent(modifiedText).withStyle(component.getStyle());
                })
                .filter(c -> c instanceof TextComponent) // Ensure it's a TextComponent
                .map(c -> (TextComponent) c) // Cast to TextComponent
                .collect(Collectors.toList()));

        event.getItemStackHolder().setLore(
                lore.stream()
                        .map(innerList -> innerList.stream() // Process each inner List<TextComponent>
                                .map(component -> {
                                    final String modifiedText = replacements.entrySet().stream()
                                            .reduce(component.getString(), (text, entry) -> text.replace(entry.getKey(), entry.getValue()), (a, b) -> b);
                                    return new TextComponent(modifiedText).withStyle(component.getStyle());
                                })
                                .filter(c -> c instanceof TextComponent) // Ensure it's a TextComponent
                                .map(c -> (TextComponent) c) // Cast to TextComponent
                                .collect(Collectors.toList()) // Collect modified components back into List<TextComponent>
                        )
                        .collect(Collectors.toList()) // Maintain the original List<List<TextComponent>> structure
        );
    }
}
