package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.*;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.Panel;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;

public final class IslandHubGui {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 164;

    private IslandHubGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id")) return;

        ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                .getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;

        String ownerName = UsernameCache.getBlocking(island.getOwner());
        boolean isAdmin = island.isOwner(player.getUUID());
        boolean isPart = island.isPartOf(player.getUUID());

        MasuGui gui = MasuGui.create("island_hub")
                .title(new TextComponent(ownerName + "'s Island"))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_3);

        gui.add(new Panel("bg", 0, 0, WIDTH, HEIGHT)
                .color(0xE8181818).border(0x333333));

        gui.add(new Button("close_btn", WIDTH - 16, 2, 12, 12)
                .label(new TextComponent("X")).backgroundColor(0xFFAA4444).flat()
                .onClick(MasuGui::closeFor));

        gui.add(new Label("title", WIDTH / 2, 6)
                .text(new TextComponent(ownerName + "'s Island").withStyle(ChatFormatting.GOLD))
                .centered().scale(1.0f).shadow(true));

        String biome = island.getBiome() != null ? island.getBiome() : "Unknown";
        String visibility = island.isVisible() ? "Public" : "Private";
        gui.add(new Label("info", WIDTH / 2, 18)
                .text(new TextComponent("Biome: " + biome + "  |  " + visibility))
                .color(0xFFAAAAAA).centered().scale(0.7f));

        gui.add(new Divider("header_div", 8, 28, WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        gui.add(new Label("actions_label", 62, 33)
                .text(new TextComponent("Quick Actions"))
                .color(0xFF888888).centered().scale(0.7f));
        gui.add(new Label("manage_label", 178, 33)
                .text(new TextComponent("Management"))
                .color(0xFF888888).centered().scale(0.7f));

        gui.add(new Divider("col_div", WIDTH / 2, 30, HEIGHT - 60)
                .vertical().color(0xFF2A2A2A));

        gui.add(new Button("teleport_btn", 10, 44, 104, 18)
                .label(new TextComponent("Teleport")).backgroundColor(0xFF383838).flat()
                .onClick(p -> {
                    MasuGui.closeFor(p);
                    island.teleportTo(p);
                }));

        if (isAdmin) {
            gui.add(new Button("spawn_btn", 10, 66, 104, 18)
                    .label(new TextComponent("Set Spawn")).backgroundColor(0xFF383838).flat()
                    .onClick(p -> ConfirmSetSpawnGui.open(p, data)));
        }

        String visibilityLabel = island.isVisible() ? "Visibility: Public" : "Visibility: Private";
        gui.add(new Button("visibility_btn", 10, 88, 104, 18)
                .label(new TextComponent(visibilityLabel)).backgroundColor(0xFF383838).flat()
                .onClick(p -> {
                    island.toggleVisibility();
                    open(p, data);
                }));

        if (isAdmin) {
            gui.add(new Button("members_btn", 126, 44, 104, 18)
                    .label(new TextComponent("Members")).backgroundColor(0xFF383838).flat()
                    .onClick(p -> {
                        MasuGui.closeFor(p);
                        GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members", data);
                    }));

            gui.add(new Button("biome_btn", 126, 66, 104, 18)
                    .label(new TextComponent("Biome")).backgroundColor(0xFF383838).flat()
                    .onClick(p -> {
                        MasuGui.closeFor(p);
                        GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:biomes", data);
                    }));

            gui.add(new Button("permissions_btn", 126, 88, 104, 18)
                    .label(new TextComponent("Permissions")).backgroundColor(0xFF383838).flat()
                    .onClick(p -> {
                        MasuGui.closeFor(p);
                        GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                    }));
        } else {
            gui.add(new Button("members_btn", 126, 44, 104, 18)
                    .label(new TextComponent("Members")).backgroundColor(0xFF383838).flat()
                    .onClick(p -> {
                        MasuGui.closeFor(p);
                        GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members", data);
                    }));
        }

        gui.add(new Divider("footer_div", 8, HEIGHT - 30, WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        if (isPart) {
            gui.add(new Button("leave_btn", 10, HEIGHT - 24, 70, 16)
                    .label(new TextComponent("Leave Island")).backgroundColor(0xFFAA4444).flat()
                    .onClick(p -> {
                        MasuGui.closeFor(p);
                        ConfirmLeaveGui.open(p, data);
                    }));
        }

        gui.openFor(player);
    }
}
