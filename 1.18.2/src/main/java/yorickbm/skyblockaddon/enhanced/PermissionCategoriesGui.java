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
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;

public final class PermissionCategoriesGui {

    private static final int WIDTH = 260;
    private static final int HEIGHT = 140;

    private PermissionCategoriesGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id") || !data.contains("group_id")) return;

        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;
        IslandGroup group = island.getGroup(data.getUUID("group_id"));
        if (group == null) return;

        boolean isOp = player.hasPermissions(2);

        MasuGui gui = MasuGui.create("permission_categories")
                .title(new TextComponent(group.getName() + " - Permissions"))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_3);

        gui.add(new Panel("bg", 0, 0, WIDTH, HEIGHT)
                .color(0xE8181818).border(0x333333));

        gui.add(new Button("close_btn", WIDTH - 16, 2, 12, 12)
                .label(new TextComponent("X")).backgroundColor(0xFFAA4444).flat()
                .onClick(MasuGui::closeFor));

        gui.add(new Label("title", WIDTH / 2, 6)
                .text(new TextComponent(group.getName() + " - Permissions").withStyle(ChatFormatting.GOLD))
                .centered().scale(1.0f).shadow(true));

        gui.add(new Divider("header_div", 8, 18, WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        int buttonWidth = 76;
        int buttonHeight = 18;
        int startX = 10;
        int startY = 24;
        int gap = 4;

        String[][] categories = {
                {"general", "General"},
                {"transport", "Transport"},
                {"redstone", "Redstone"},
                {"storage", "Storage"},
                {"interactables", "Interactables"},
                {"vaulthunters", "Vault Hunters"}
        };

        int col = 0;
        int row = 0;
        for (String[] category : categories) {
            int x = startX + col * (buttonWidth + gap);
            int y = startY + row * (buttonHeight + gap);

            gui.add(new Button("cat_" + category[0], x, y, buttonWidth, buttonHeight)
                    .label(new TextComponent(category[1])).backgroundColor(0xFF383838).flat()
                    .onClick(p -> {
                        CompoundTag newData = data.copy();
                        CompoundTag libData = newData.getCompound(GUILibraryRegistry.MOD_ID);
                        libData.putString("category_id", category[0]);
                        newData.put(GUILibraryRegistry.MOD_ID, libData);
                        MasuGui.closeFor(p);
                        PermissionTogglesGui.open(p, newData, category[0], group);
                    }));

            col++;
            if (col >= 3) {
                col = 0;
                row++;
            }
        }

        if (isOp) {
            int x = startX;
            int y = startY + (row + (col > 0 ? 1 : 0)) * (buttonHeight + gap);
            gui.add(new Button("cat_admin", x, y, buttonWidth * 2 + gap, buttonHeight)
                    .label(new TextComponent("Admin Controls")).backgroundColor(0xFF553355).flat()
                    .onClick(p -> {
                        CompoundTag newData = data.copy();
                        CompoundTag libData = newData.getCompound(GUILibraryRegistry.MOD_ID);
                        libData.putString("category_id", "admin_controls");
                        newData.put(GUILibraryRegistry.MOD_ID, libData);
                        MasuGui.closeFor(p);
                        PermissionTogglesGui.open(p, newData, "admin_controls", group);
                    }));
        }

        gui.add(new Divider("footer_div", 8, HEIGHT - 28, WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        gui.add(new Button("back_btn", 10, HEIGHT - 22, 32, 14)
                .label(new TextComponent("Back")).backgroundColor(0xFFAA4444).flat()
                .onClick(p -> {
                    MasuGui.closeFor(p);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                }));

        boolean isDefaultGroup = group.getId().equals(SkyblockAddonCore.MOD_UUID)
                || group.getId().equals(SkyblockAddonCore.MOD_UUID2);

        if (!isDefaultGroup) {
            gui.add(new Button("remove_btn", 48, HEIGHT - 22, 82, 14)
                    .label(new TextComponent("Remove Group")).backgroundColor(0xFF882222).flat()
                    .onClick(p -> {
                        MasuGui.closeFor(p);
                        ConfirmRemoveGroupGui.open(p, data, island, group);
                    }));
        }

        gui.add(new Button("members_btn", WIDTH - 10 - 56, HEIGHT - 22, 56, 14)
                .label(new TextComponent("Members")).backgroundColor(0xFF383838).flat()
                .onClick(p -> {
                    MasuGui.closeFor(p);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:members_group", data);
                }));

        gui.openFor(player);
    }
}
