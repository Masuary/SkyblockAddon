package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.GuiElement;
import com.masuary.masugui.element.data.KeyValueRow;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;
import yorickbm.skyblockaddon.islands.IslandActionService;

import java.util.List;
import java.util.Objects;

public final class ConfirmCreateGroupGui {

    private ConfirmCreateGroupGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains(GuiDataKeys.ISLAND_ID)) return;

        ItemStack heldItem = player.getMainHandItem();
        boolean hasItem = !heldItem.isEmpty() && heldItem.getItem() != Items.AIR;
        String question = hasItem ? "Create group " + ForgeIslandGroup.nameOf(heldItem) + "?" : "Create a group?";
        List<KeyValueRow> terms = hasItem
                ? List.of(KeyValueRow.of("Name", ForgeIslandGroup.nameOf(heldItem), EnhancedDialog.VALUE_AQUA),
                        KeyValueRow.of("Icon", GuiElement.prettifyItemName(new ItemStack(heldItem.getItem())), EnhancedDialog.VALUE_WHITE),
                        KeyValueRow.of("Rules", "server defaults", EnhancedDialog.VALUE_WHITE))
                : List.of(KeyValueRow.of("Held item", "hold an item first", EnhancedDialog.VALUE_RED),
                        KeyValueRow.of("Icon and name", "from the held item", EnhancedDialog.VALUE_WHITE));
        EnhancedDialog.open(player, "confirm_create_group", "Create group", hasItem ? heldItem.copy() : new ItemStack(Items.OAK_SAPLING),
                question, terms, "Create", ButtonStyle.PRIMARY, hasItem ? p -> createGroup(p, data) : null,
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data));
    }

    private static void createGroup(ServerPlayer player, CompoundTag data) {
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;
        ItemStack held = player.getMainHandItem().copy();
        String newGroupName = ForgeIslandGroup.nameOf(held);
        IslandActionResult result = IslandActionService.createGroupFromHeldItem(player, island);
        if (result.succeeded()) {
            player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.created")
                    .formatted(newGroupName, Objects.requireNonNull(held.getItem().getRegistryName()).getPath()))
                    .withStyle(ChatFormatting.GREEN), player.getUUID());
        } else {
            IslandActionService.sendFailure(player, result, newGroupName);
        }
        GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:groups", data);
    }
}
