package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;

import java.util.List;

public final class ConfirmRemoveGroupGui {

    private ConfirmRemoveGroupGui() {}

    public static void open(ServerPlayer player, CompoundTag data, Island island, IslandGroup group) {
        EnhancedDialog.open(player, "confirm_remove_group", "Remove group?", new ItemStack(Items.BARRIER),
                List.of(new TextComponent("Remove group '" + group.getName() + "'?"),
                        new TextComponent("This cannot be undone.").withStyle(ChatFormatting.GRAY)),
                "Remove", ButtonStyle.DANGER,
                p -> {
                    island.removeGroup(group.getId());
                    p.sendMessage(new TextComponent("Group '" + group.getName() + "' removed.").withStyle(ChatFormatting.RED), p.getUUID());
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                },
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:permissions", data));
    }
}
