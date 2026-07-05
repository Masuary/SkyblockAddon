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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.IslandAdministrationService;

public final class ConfirmCreateGroupGui {

    private static final int WIDTH = 220;
    private static final int HEIGHT = 110;

    private ConfirmCreateGroupGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id")) return;
        final Island targetIsland = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
        if (!EnhancedGuiHelper.canManageIsland(player, targetIsland)) return;

        ItemStack heldItem = player.getMainHandItem();
        boolean hasItem = !heldItem.isEmpty() && heldItem.getItem() != Items.AIR;
        String itemName = hasItem ? heldItem.getDisplayName().getString().trim() : "Nothing";

        MasuGui gui = MasuGui.create("confirm_create_group")
                .title(new TextComponent("Create Group"))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_1);

        gui.add(new Panel("bg", 0, 0, WIDTH, HEIGHT)
                .color(0xE8181818).border(0xFF335533));

        gui.add(new Label("title", WIDTH / 2, 6)
                .text(new TextComponent("Create Group").withStyle(ChatFormatting.GREEN))
                .centered().scale(1.0f).shadow(true));

        gui.add(new Divider("div1", 8, 18, WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        gui.add(new Label("info1", WIDTH / 2, 24)
                .text(new TextComponent("A group will be created using the"))
                .color(0xFFCCCCCC).centered().scale(0.7f));
        gui.add(new Label("info2", WIDTH / 2, 34)
                .text(new TextComponent("item in your main hand as the icon."))
                .color(0xFFCCCCCC).centered().scale(0.7f));
        gui.add(new Label("info3", WIDTH / 2, 44)
                .text(new TextComponent("The item's name will be the group name."))
                .color(0xFFCCCCCC).centered().scale(0.7f));

        if (hasItem) {
            gui.add(new ItemDisplay("held_item", WIDTH / 2 - 8, 56)
                    .item(heldItem.copy()));

            gui.add(new Label("item_name", WIDTH / 2, 74)
                    .text(new TextComponent("\"" + itemName + "\"").withStyle(ChatFormatting.AQUA))
                    .centered().scale(0.7f));
        } else {
            gui.add(new Label("no_item", WIDTH / 2, 60)
                    .text(new TextComponent("You are not holding an item!").withStyle(ChatFormatting.RED))
                    .centered().scale(0.7f));
        }

        gui.add(new Divider("div2", 8, HEIGHT - 26, WIDTH - 16)
                .horizontal().color(0xFF3A3A3A));

        if (hasItem) {
            gui.add(new Button("confirm_btn", WIDTH / 2 - 72, HEIGHT - 20, 64, 14)
                    .label(new TextComponent("Create")).backgroundColor(0xFF336633).flat()
                    .onClick(p -> {
                        ItemStack held = p.getMainHandItem();
                        if (held.isEmpty() || held.getItem() == Items.AIR) {
                            p.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.failure"))
                                    .withStyle(ChatFormatting.RED), p.getUUID());
                            MasuGui.closeFor(p);
                            GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                            return;
                        }

                        Island island = IslandManager.getInstance().getIslandByUUID(data.getUUID("island_id"));
                        if (!EnhancedGuiHelper.canManageIsland(p, island)) return;

                        String newGroupName = held.getDisplayName().getString().trim();
                        boolean nameExists = island.getGroups().stream()
                                .anyMatch(g -> g.getName().equalsIgnoreCase(newGroupName));

                        if (nameExists) {
                            p.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.name.exists")
                                    .formatted(newGroupName)).withStyle(ChatFormatting.RED), p.getUUID());
                            MasuGui.closeFor(p);
                            GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                            return;
                        }

                        if (IslandAdministrationService.createGroup(p, island, held)) {
                            p.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.created")
                                    .formatted(newGroupName, held.getItem().getRegistryName().toString().split(":")[1].trim()))
                                    .withStyle(ChatFormatting.GREEN), p.getUUID());
                        }

                        MasuGui.closeFor(p);
                        GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                    }));
        }

        gui.add(new Button("cancel_btn", WIDTH / 2 + 8, HEIGHT - 20, 64, 14)
                .label(new TextComponent("Cancel")).backgroundColor(0xFF383838).flat()
                .onClick(p -> {
                    MasuGui.closeFor(p);
                    GUILibraryRegistry.openGUIForPlayer(p, "skyblockaddon:groups", data);
                }));

        gui.openFor(player);
    }
}
