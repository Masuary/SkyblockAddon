package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.data.KeyValueRow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConfirmLeaveGui {

    private ConfirmLeaveGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains(GuiDataKeys.ISLAND_ID)) return;

        ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                .getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;

        MinecraftServer server = Objects.requireNonNull(player.getServer());
        String ownerName = PlayerNames.nameOf(server, island.getOwner());
        EnhancedDialog.open(player, "confirm_leave", "Leave island", new ItemStack(Items.TNT),
                "Leave " + ownerName + "'s island?", terms(server, island, island.isOwner(player.getUUID())),
                "Leave", ButtonStyle.DANGER,
                p -> Objects.requireNonNull(p.getServer()).getCommands().performCommand(p.createCommandSourceStack(), "/island leave"),
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:overview", data));
    }

    /** An owner who leaves hands the island to the first member, or leaves it ownerless and private without members. */
    private static List<KeyValueRow> terms(MinecraftServer server, ForgeIsland island, boolean leaverIsOwner) {
        List<KeyValueRow> terms = new ArrayList<>();
        if (leaverIsOwner) {
            terms.add(island.getMembers().isEmpty()
                    ? KeyValueRow.of("Island", "ownerless and private", EnhancedDialog.VALUE_RED)
                    : KeyValueRow.of("New owner", PlayerNames.nameOf(server, island.getMembers().get(0)), EnhancedDialog.VALUE_WHITE));
        }
        terms.add(KeyValueRow.of("You become", "a visitor", EnhancedDialog.VALUE_RED));
        terms.add(KeyValueRow.of("Undo", "chat link, 15 minutes", EnhancedDialog.VALUE_WHITE));
        return terms;
    }
}
