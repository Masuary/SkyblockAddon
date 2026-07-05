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
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.IslandAdministrationService;

public final class ConfirmRemoveGroupGui {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 80;

    private ConfirmRemoveGroupGui() {}

    public static void open(ServerPlayer player, CompoundTag data, Island island, IslandGroup group) {
        if (!EnhancedGuiHelper.canManageIsland(player, island)) return;
        MasuGui gui = MasuGui.create("confirm_remove_group")
                .title(new TextComponent("Remove Group"))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_1);

        gui.add(new Panel("bg", 0, 0, WIDTH, HEIGHT)
                .color(0xE8181818).border(0xFF553333));

        gui.add(new Label("title", WIDTH / 2, 8)
                .text(new TextComponent("Remove Group?").withStyle(ChatFormatting.RED))
                .centered().scale(1.0f).shadow(true));

        gui.add(new Label("message", WIDTH / 2, 24)
                .text(new TextComponent("Are you sure you want to remove"))
                .color(0xFFCCCCCC).centered().scale(0.7f));

        gui.add(new Label("message2", WIDTH / 2, 34)
                .text(new TextComponent("'" + group.getName() + "'?").withStyle(ChatFormatting.WHITE))
                .color(0xFFCCCCCC).centered().scale(0.7f));

        gui.add(new Button("confirm_btn", WIDTH / 2 - 72, HEIGHT - 26, 64, 16)
                .label(new TextComponent("Remove")).backgroundColor(0xFFAA3333).flat()
                .onClick(p -> {
                    MasuGui.closeFor(p);
                    if (IslandAdministrationService.removeGroup(p, island, group.getId())) {
                        p.sendMessage(new TextComponent("Group '" + group.getName() + "' removed.")
                                .withStyle(ChatFormatting.RED), p.getUUID());
                    }
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                }));

        gui.add(new Button("cancel_btn", WIDTH / 2 + 8, HEIGHT - 26, 64, 16)
                .label(new TextComponent("Cancel")).backgroundColor(0xFF383838).flat()
                .onClick(p -> {
                    MasuGui.closeFor(p);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:permissions", data);
                }));

        gui.openFor(player);
    }
}
