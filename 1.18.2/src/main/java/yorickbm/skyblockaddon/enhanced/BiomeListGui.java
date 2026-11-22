package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.KeyValueRow;
import com.masuary.masugui.element.data.ListRow;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.components.ItemStackComponent;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.registries.BiomeRegistry;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.BiomeLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The biomes from the biome registry beside the selected biome's card; Apply changes the island biome. */
final class BiomeListGui {

    private static final String SELECTED_KEY = "selected_biome";
    private static final int CURRENT_COLOR = 0x55FF55;

    private record BiomeEntry(String biomeId, String displayName, ItemStack icon, List<Component> lore) {
    }

    private BiomeListGui() {}

    static void open(ServerPlayer player, CompoundTag data) {
        if (!data.hasUUID(GuiDataKeys.ISLAND_ID)) return;
        ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;

        String currentBiome = island.getBiome() == null ? "" : island.getBiome();
        List<BiomeEntry> biomes = biomeEntries(player);
        String stored = data.getString(SELECTED_KEY);
        int selected = DetailListScreen.selectedIndex(biomes,
                entry -> stored.isEmpty() ? entry.biomeId().equals(currentBiome) : entry.biomeId().equals(stored));

        List<ListRow> rows = new ArrayList<>();
        for (BiomeEntry entry : biomes) {
            boolean isCurrent = entry.biomeId().equals(currentBiome);
            ListRow row = ListRow.of(entry.icon().copy(), entry.displayName() + (isCurrent ? " (current)" : "")).withTooltip(entry.lore());
            rows.add(isCurrent ? row.withStatus(CURRENT_COLOR) : row);
        }

        String count = EnhancedGuiHelper.countOf(biomes.size(), "biome", "biomes");
        MasuGui gui = DetailListScreen.frame("island_biomes", DetailListScreen.PLAYERS, "Biome", count, new ItemStack(Items.GRASS_BLOCK),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:settings", data),
                List.of(new KeyHint("Click", "a biome for details"), new KeyHint("Hover", "for its mobs and plants")),
                "Now: " + EnhancedGuiHelper.biomeName(currentBiome));
        DetailListScreen.table(gui, DetailListScreen.PLAYERS, "biomes", "Biome", rows, List.of(), selected)
                .emptyText(new TextComponent("No biomes in the biome registry"))
                .fallbackSearch(true)
                .onClick((p, index, click) -> {
                    if (index < 0 || index >= biomes.size()) return;
                    CompoundTag selection = data.copy();
                    selection.putString(SELECTED_KEY, biomes.get(index).biomeId());
                    open(p, selection);
                });
        DetailListScreen.detail(gui, DetailListScreen.PLAYERS,
                selected < 0 ? null : detail(island, data, biomes.get(selected), biomes.get(selected).biomeId().equals(currentBiome)),
                "No biome selected");
        gui.openFor(player);
    }

    private static DetailListScreen.Detail detail(ForgeIsland island, CompoundTag data, BiomeEntry entry, boolean isCurrent) {
        List<KeyValueRow> facts = List.of(
                KeyValueRow.of("Status", isCurrent ? "Current biome" : "Not applied", isCurrent ? CURRENT_COLOR : EnhancedDialog.VALUE_GRAY),
                KeyValueRow.of("Biome id", entry.biomeId(), EnhancedDialog.VALUE_WHITE));
        DetailListScreen.DetailButton apply = new DetailListScreen.DetailButton("apply", "Apply", ButtonStyle.PRIMARY, !isCurrent,
                p -> apply(p, data, island, entry.biomeId()));
        return new DetailListScreen.Detail(entry.icon(), entry.lore(), entry.displayName(), facts, List.of(), apply);
    }

    private static void apply(ServerPlayer player, CompoundTag data, ForgeIsland island, String biomeId) {
        if (EnhancedGuiHelper.closeWhenDenied(player, IslandActionService.canManage(island, player), IslandActionResult.NOT_ALLOWED)) return;
        MasuGui.closeFor(player);
        island.updateBiome(biomeId, player.server);
        player.sendMessage(new TextComponent("Biome updated to " + biomeId).withStyle(ChatFormatting.GREEN), player.getUUID());
        GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:biomes", data);
    }

    private static List<BiomeEntry> biomeEntries(ServerPlayer player) {
        BiomeRegistry biomeRegistry = new BiomeRegistry(
                FMLPaths.CONFIGDIR.get(),
                "minecraft:dead_bush",
                ForgeRegistries.BIOMES.getValues().stream()
                        .filter(biome -> biome.getRegistryName() != null)
                        .map(biome -> biome.getRegistryName().toString())
                        .toList());

        List<BiomeEntry> entries = new ArrayList<>();
        while (biomeRegistry.hasNext()) {
            ItemStackComponent component = new ItemStackComponent();
            biomeRegistry.getNextData(component);
            String biomeId = component.getObject("biome", String.class);
            String displayName = component.getObject("name", String.class);
            List<Component> lore = new ArrayList<>(BiomeLore.build(biomeId, player.getLevel()));
            entries.add(new BiomeEntry(biomeId, displayName, new ItemStack(iconItem(biomeRegistry.getDataForComponent(component))), lore));
        }
        return entries;
    }

    private static Item iconItem(Optional<String> iconId) {
        if (iconId.isEmpty()) return Items.PAPER;
        Item resolved = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(iconId.get()));
        return resolved == null || resolved == Items.AIR ? Items.PAPER : resolved;
    }
}
