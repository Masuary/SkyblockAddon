package yorickbm.skyblockaddon.gui.island;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import yorickbm.skyblockaddon.SkyblockAddon;
import yorickbm.skyblockaddon.capabilities.providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.gui.ServerOnlyHandler;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.ServerHelper;
import yorickbm.skyblockaddon.util.UsernameCache;

import java.util.List;
import java.util.UUID;

public class MemberOverviewHandler extends ServerOnlyHandler<IslandData> {
    private static final Logger LOGGER = LogManager.getLogger();
    protected MemberOverviewHandler(int syncId, Inventory playerInventory, IslandData data) {
        super(syncId, playerInventory, 4, data);
    }

    public static void openMenu(Player player, IslandData data) {
        MenuProvider fac = new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return new TextComponent(data.getOwner(player.getServer()).getName() + "'s island");
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(int syncId, @NotNull Inventory inv, @NotNull Player player) {
                return new MemberOverviewHandler(syncId, inv, data);
            }
        };
        player.openMenu(fac);
    }

    @Override
    protected boolean isRightSlot(int slot) {
        return slot == 35 || slot == 31 || (slot > 10 && slot <= 25 && slot%9 != 0 && slot%9 != 8) ;
    }

    @Override
    protected void fillInventoryWith(Player player) {
        int memberIndex = 0;
        List<UUID> members = this.data.getMembers();

        for(int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack item;

            if (i == 35) {
                item = new ItemStack(Items.ARROW);
                item.setHoverName(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.default.back"), ChatFormatting.RED, ChatFormatting.BOLD));
            } else if (i == 31 && this.data.isIslandAdmin(player.getUUID())) {
                item = new ItemStack(Items.OAK_BOAT);
                item.setHoverName(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.invite.title"), ChatFormatting.GREEN, ChatFormatting.BOLD));
                ServerHelper.addLore(item, ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.invite.desc"), ChatFormatting.GRAY));
            } else if(i == 10) {
                String playerName = this.data.hasOwner() ? this.data.getOwner(player.getServer()).getName() : SkyblockAddonLanguageConfig.getForKey("guis.default.unknown");

                item = new ItemStack(Items.PLAYER_HEAD);
                item.setHoverName(
                        ServerHelper.formattedText(
                                SkyblockAddonLanguageConfig.getForKey("guis.currentowner.title").formatted(playerName),
                                ChatFormatting.GOLD, ChatFormatting.BOLD
                        )
                );
                ServerHelper.addLore(item, ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.currentowner.desc"), ChatFormatting.GRAY));

                CompoundTag tag = item.getOrCreateTag();
                if(this.data.hasOwner()) tag.putString("SkullOwner", playerName);
                item.setTag(tag);


            } else if(i > 10 && i <= 25 && i%9 != 0 && i%9 != 8) {
                if(memberIndex < members.size()) {
                    String playerName = SkyblockAddonLanguageConfig.getForKey("guis.default.unknown");
                    try { playerName = UsernameCache.getBlocking(members.get(memberIndex)); } catch( Exception ex) {
                        LOGGER.error(ex);
                    }

                    item = new ItemStack(Items.PLAYER_HEAD);
                    item.setHoverName(ServerHelper.formattedText(playerName, ChatFormatting.BLUE, ChatFormatting.BOLD));

                    CompoundTag tag = item.getOrCreateTag();
                    if(this.data.hasOwner()) tag.putString("SkullOwner", playerName);
                    item.setTag(tag);

                    if(this.data.isIslandAdmin(player.getUUID())) {
                        UUID member = members.get(memberIndex);
                        item.getOrCreateTagElement(SkyblockAddon.MOD_ID).putString("member", member.toString()); //Put member in item NBT for click event

                        if(this.data.isIslandAdmin(member)) {
                            ServerHelper.addLore(item,
                                ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.admin.desc"), ChatFormatting.GRAY),
                                    ServerHelper.formattedText("", ChatFormatting.GRAY),
                                    ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.admin.rightclick.desc"), ChatFormatting.GRAY)
                            );
                            item.setHoverName(ServerHelper.formattedText(playerName, ChatFormatting.RED, ChatFormatting.BOLD));

                        } else {
                            ServerHelper.addLore(item,
                                ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.member.desc"), ChatFormatting.GRAY),
                                ServerHelper.formattedText("", ChatFormatting.GRAY),
                                ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.member.rightclick.desc"), ChatFormatting.GRAY),
                                ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.member.leftclick.desc"), ChatFormatting.GRAY)
                            );
                        }
                    } else {
                        UUID member = members.get(memberIndex);
                        if(this.data.isIslandAdmin(member)) {
                            ServerHelper.addLore(item,
                                    ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.admin.desc"), ChatFormatting.GRAY)
                            );
                            item.setHoverName(ServerHelper.formattedText(playerName, ChatFormatting.RED, ChatFormatting.BOLD));
                        } else {
                            ServerHelper.addLore(item,
                                    ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.member.desc"), ChatFormatting.GRAY)
                            );
                        }
                    }

                    memberIndex += 1;
                } else {
                    item = new ItemStack(Items.AIR);
                }
            } else {
                item = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                item.setHoverName(new TextComponent(""));
            }

            setItem(i, 0, item);
        }
    }

    @Override
    protected boolean handleSlotClicked(ServerPlayer player, int index, Slot slot, int clickType) {
        switch (index) {
            case 35 -> {
                player.closeContainer();
                player.getServer().execute(() -> IslandOverviewHandler.openMenu(player, this.data));
                ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                return true;
            }
            case 31 -> {
                player.closeContainer();
                player.getServer().execute(() -> InviteOverviewHandler.openMenu(player, this.data));
                ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                return true;
            }
            default -> {
                if (!this.data.isIslandAdmin(player.getUUID())) return false;
                UUID member = UUID.fromString(slot.getItem().getTagElement(SkyblockAddon.MOD_ID).getString("member"));
                if (!this.data.isIslandAdmin(member))
                    switch (clickType) {
                        case 1 -> {
                            this.data.removeIslandMember(member);
                            Player member_player = player.getServer().getPlayerList().getPlayer(member);
                            if (member_player != null) {
                                member_player.getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(island -> {
                                    island.setIsland(null);
                                    member_player.teleportTo(player.getLevel().getSharedSpawnPos().getX(), player.getLevel().getSharedSpawnPos().getY(), player.getLevel().getSharedSpawnPos().getZ());
                                    member_player.sendMessage(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("island.member.kick"), ChatFormatting.GREEN), member);
                                });
                            }
                        }
                        case 0 -> this.data.makeAdmin(member);
                    }
                else
                    switch (clickType) {
                        case 1:
                            this.data.removeAdmin(member);
                            break;
                        case 0:
                            break;
                    }
                ServerHelper.playSongToPlayer(player, SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 1f);
                this.fillInventoryWith(player);
                return true;
            }
        }
    }
}
