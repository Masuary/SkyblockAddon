package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.List;
import java.util.Objects;

public final class ConfirmLeaveGui {

    private ConfirmLeaveGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id")) return;

        ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                .getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;

        String ownerName = UsernameCache.getBlocking(island.getOwner());
        EnhancedDialog.open(player, "confirm_leave", "Leave island?", new ItemStack(Items.TNT),
                List.of(new TextComponent("Leave " + ownerName + "'s island?"),
                        new TextComponent("You need a new invite to come back.").withStyle(ChatFormatting.GRAY)),
                "Leave", ButtonStyle.DANGER,
                p -> Objects.requireNonNull(p.getServer()).getCommands().performCommand(p.createCommandSourceStack(), "/island leave"),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:overview", data));
    }
}
