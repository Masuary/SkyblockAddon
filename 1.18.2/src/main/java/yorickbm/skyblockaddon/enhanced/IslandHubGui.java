package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.CardGrid;
import com.masuary.masugui.element.StatusBar;
import com.masuary.masugui.element.Window;
import com.masuary.masugui.element.data.Card;
import com.masuary.masugui.element.data.Chip;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The island menu: one card per action with its current state; staff-only actions are hidden from members. */
public final class IslandHubGui {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 168;

    private record Action(Card card, Consumer<ServerPlayer> run) {
    }

    private IslandHubGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id")) return;

        ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                .getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;

        String ownerName = UsernameCache.getBlocking(island.getOwner());
        boolean hasAdminGroupPermission = island.getGroupForEntityUUID(player.getUUID())
                .map(g -> g.canDo("admin_menu"))
                .orElse(true);
        boolean isAdmin = island.isOwner(player.getUUID())
                || player.hasPermissions(Commands.LEVEL_ADMINS)
                || hasAdminGroupPermission;
        boolean isPart = island.isPartOf(player.getUUID());
        String rawBiome = island.getBiome() != null ? island.getBiome() : "Unknown";
        String biome = rawBiome.contains(":") ? rawBiome.substring(rawBiome.indexOf(':') + 1) : rawBiome;
        String visibility = island.isVisible() ? "Public" : "Private";

        List<Action> actions = new ArrayList<>();
        actions.add(new Action(Card.of(new ItemStack(Items.ENDER_PEARL), "Teleport", "Go to the island spawn"), p -> {
            MasuGui.closeFor(p);
            island.teleportTo(p);
        }));
        if (isAdmin) {
            actions.add(new Action(Card.of(new ItemStack(Items.LODESTONE), "Set spawn", "Use your position"),
                    p -> ConfirmSetSpawnGui.open(p, data)));
        }
        actions.add(new Action(Card.of(new ItemStack(Items.ENDER_EYE), "Visibility", visibility + ", click to "
                + (island.isVisible() ? "hide" : "show")), p -> {
            island.toggleVisibility();
            open(p, data);
        }));
        actions.add(new Action(Card.of(new ItemStack(Items.PLAYER_HEAD), "Members", island.getMembers().size() + " members"),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members", data)));
        if (isAdmin) {
            actions.add(new Action(Card.of(new ItemStack(Items.GRASS_BLOCK), "Biome", biome),
                    p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:biomes", data)));
            actions.add(new Action(Card.of(new ItemStack(Items.WRITABLE_BOOK), "Permissions", island.getGroups().size() + " groups"),
                    p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data)));
        }

        MasuGui gui = MasuGui.create("island_hub")
                .title(new TextComponent(ownerName + "'s Island"))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_3);
        gui.add(new Window("window", WIDTH, HEIGHT).title(new TextComponent(ownerName + "'s Island")).accent(EnhancedDialog.ACCENT)
                .chip(Chip.of("", biome, 0xAAAAAA))
                .chip(Chip.of("", visibility, island.isVisible() ? 0x55FF55 : 0xAAAAAA))
                .chip(Chip.of("Members", String.valueOf(island.getMembers().size()), 0xFFFFFF))
                .fallbackIcon(new ItemStack(Items.GRASS_BLOCK)));
        gui.add(new CardGrid("actions", 8, 28, WIDTH - 16, 108).columns(2).cardHeight(32).gap(4)
                .cards(actions.stream().map(Action::card).toList())
                .onClick((p, index, click) -> {
                    if (index >= 0 && index < actions.size()) actions.get(index).run().accept(p);
                }));
        if (isPart) {
            gui.add(new Button("leave", 8, HEIGHT - 31, 80, 15).style(ButtonStyle.DANGER)
                    .label(new TextComponent("Leave island"))
                    .onClick(p -> ConfirmLeaveGui.open(p, data)).fallbackSlot(18));
        }
        gui.add(new StatusBar("status", 1, HEIGHT - 13, WIDTH - 2).hints(List.of(new KeyHint("Click", "Open")))
                .right(new TextComponent("Owner: " + ownerName)));
        gui.openFor(player);
    }
}
