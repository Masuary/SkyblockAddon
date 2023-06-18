package yorickbm.skyblockaddon.gui.island;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import yorickbm.skyblockaddon.SkyblockAddon;
import yorickbm.skyblockaddon.capabilities.Providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.commands.LeaveIslandCommand;
import yorickbm.skyblockaddon.gui.ServerOnlyHandler;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.ServerHelper;

public class IslandOverviewHandler extends ServerOnlyHandler<IslandData> {

    private IslandOverviewHandler(int syncId, Inventory playerInventory, IslandData data) {
        super(syncId, playerInventory, 3, data);
    }

    public static void openMenu(Player player, IslandData data) {
        MenuProvider fac = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return new TextComponent(data.getOwner(player.getServer()).getName() + "'s island");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
                SkyblockAddon.islandUIIds.add(syncId);
                return new IslandOverviewHandler(syncId, inv, data);
            }
        };
        player.openMenu(fac);
    }

    @Override
    protected boolean isRightSlot(int slot) {
        return slot >= 10 && slot <= 16;
    }

    @Override
    protected void fillInventoryWith(Player player) {
        for(int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack item = null;

            if(this.data.isAdmin(player.getUUID())) {
                switch (i) {

                    case 10:
                        item = new ItemStack(Items.ENDER_EYE);
                        item.setHoverName(ServerHelper.formattedText("Teleport", ChatFormatting.BOLD, ChatFormatting.BLUE));
                        ServerHelper.addLore(item, ServerHelper.formattedText("\u00BB Teleport to your islands spawn location.", ChatFormatting.GRAY));
                        break;
                    case 12:
                        item = new ItemStack(Items.CHEST);
                        item.setHoverName(ServerHelper.formattedText("Members", ChatFormatting.BOLD, ChatFormatting.BLUE));
                        ServerHelper.addLore(item, ServerHelper.formattedText("\u00BB Overview all islands members and invite others.", ChatFormatting.GRAY));
                        break;
                    case 14:
                        item = new ItemStack(Items.ANVIL);
                        item.setHoverName(ServerHelper.formattedText("Settings", ChatFormatting.BOLD, ChatFormatting.BLUE));
                        ServerHelper.addLore(item, ServerHelper.formattedText("\u00BB Change the settings of your island.", ChatFormatting.GRAY));
                        break;
                    case 16:
                        item = new ItemStack(Items.BARRIER);
                        item.setHoverName(ServerHelper.formattedText("Leave", ChatFormatting.BOLD, ChatFormatting.RED));
                        ServerHelper.addLore(item, ServerHelper.formattedText("\u00BB Leave this island and teleport to spawn.", ChatFormatting.GRAY));
                        break;

                    default:
                        item = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                        item.setHoverName(new TextComponent(""));
                        break;
                }
            } else {
                switch (i) {
                    case 10:
                        item = new ItemStack(Items.ENDER_EYE);
                        item.setHoverName(ServerHelper.formattedText("Teleport", ChatFormatting.BOLD, ChatFormatting.BLUE));
                        ServerHelper.addLore(item, ServerHelper.formattedText("\u00BB Teleport to your islands spawn location.", ChatFormatting.GRAY));
                        break;
                    case 16:
                        item = new ItemStack(Items.BARRIER);
                        item.setHoverName(ServerHelper.formattedText("Leave", ChatFormatting.BOLD, ChatFormatting.RED));
                        ServerHelper.addLore(item, ServerHelper.formattedText("\u00BB Leave this island and teleport to spawn.", ChatFormatting.GRAY));
                        break;
                    case 13:
                        item = new ItemStack(Items.CHEST);
                        item.setHoverName(ServerHelper.formattedText("Members", ChatFormatting.BOLD, ChatFormatting.BLUE));
                        ServerHelper.addLore(item, ServerHelper.formattedText("\u00BB Overview all islands members and invite others.", ChatFormatting.GRAY));
                        break;

                    default:
                        item = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                        item.setHoverName(new TextComponent(""));
                        break;
                }
            }
            setItem(i, 0, item);
        }
    }

    @Override
    protected boolean handleSlotClicked(ServerPlayer player, int index, Slot slot, int clickType) {

        if (index == 0) {
            player.closeContainer();
            ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
            return true;
        }

        if(this.data.isAdmin(player.getUUID())) {
            switch (index) {

                case 10:
                    player.closeContainer();
                    this.data.teleport(player);
                    return true;
                case 12:
                    player.closeContainer();
                    player.getServer().execute(() -> MemberOverviewHandler.openMenu(player, this.data));
                    ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                    return true;
                case 14:
                    player.closeContainer();
                    player.getServer().execute(() -> SettingsOverviewHandler.openMenu(player, this.data));
                    ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                    return true;
                case 16:
                    player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(pdata -> {
                        player.closeContainer();
                        LeaveIslandCommand.leaveIsland(this.data, pdata, player, player.getLevel());
                    });
                    ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                    return true;
            }
        } else {
            switch (index) {
                case 10:
                    player.closeContainer();
                    this.data.teleport(player);
                    return true;
                case 13:
                    player.closeContainer();
                    player.getServer().execute(() -> MemberOverviewHandler.openMenu(player, this.data));
                    ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                    return true;
                case 16:
                    player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(pdata -> {
                        player.closeContainer();
                        LeaveIslandCommand.leaveIsland(this.data, pdata, player, player.getLevel());
                    });
                    ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                    return true;
            }
        }
        return false;
    }
}
