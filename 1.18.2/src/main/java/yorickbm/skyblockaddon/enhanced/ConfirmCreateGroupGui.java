package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.element.ButtonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;

import java.util.List;
import java.util.UUID;

public final class ConfirmCreateGroupGui {

    private ConfirmCreateGroupGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id")) return;

        ItemStack heldItem = player.getMainHandItem();
        boolean hasItem = !heldItem.isEmpty() && heldItem.getItem() != Items.AIR;
        List<Component> lines = hasItem
                ? List.of(new TextComponent("New group: ").append(new TextComponent(heldItem.getDisplayName().getString().trim())
                                .withStyle(ChatFormatting.AQUA)),
                        new TextComponent("The held item is the icon and its").withStyle(ChatFormatting.GRAY),
                        new TextComponent("name is the group name.").withStyle(ChatFormatting.GRAY))
                : List.of(new TextComponent("Hold an item first.").withStyle(ChatFormatting.RED),
                        new TextComponent("The held item becomes the group's").withStyle(ChatFormatting.GRAY),
                        new TextComponent("icon and name.").withStyle(ChatFormatting.GRAY));
        EnhancedDialog.open(player, "confirm_create_group", "Create group", hasItem ? heldItem.copy() : new ItemStack(Items.OAK_SAPLING),
                lines, "Create", ButtonStyle.PRIMARY, hasItem ? p -> createGroup(p, data) : null,
                p -> GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data));
    }

    private static void createGroup(ServerPlayer player, CompoundTag data) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getItem() == Items.AIR) {
            player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.failure"))
                    .withStyle(ChatFormatting.RED), player.getUUID());
            GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:groups", data);
            return;
        }
        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;
        String newGroupName = held.getDisplayName().getString().trim();
        boolean nameExists = island.getGroups().stream().anyMatch(group -> group.getName().equalsIgnoreCase(newGroupName));
        if (nameExists) {
            player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.name.exists")
                    .formatted(newGroupName)).withStyle(ChatFormatting.RED), player.getUUID());
            GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:groups", data);
            return;
        }
        island.addGroup(new ForgeIslandGroup(UUID.randomUUID(), held.copy(), false));
        player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.created")
                .formatted(newGroupName, held.getItem().getRegistryName().toString().split(":")[1].trim()))
                .withStyle(ChatFormatting.GREEN), player.getUUID());
        GUILibraryRegistry.openGUIForPlayer(player, "skyblockaddon:groups", data);
    }
}
