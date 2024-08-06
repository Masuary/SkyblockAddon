package yorickbm.skyblockaddon.gui.travel;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import yorickbm.skyblockaddon.SkyblockAddon;
import yorickbm.skyblockaddon.capabilities.IslandGenerator;
import yorickbm.skyblockaddon.capabilities.PlayerIsland;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.gui.ServerOnlyHandler;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class TeleportIslandOverviewHandler extends ServerOnlyHandler<IslandGenerator> {

    private final List<IslandData> islands;
    private final int pages;
    private int page = 0;
    private final MinecraftServer server;
    private final PlayerIsland data2;

    protected TeleportIslandOverviewHandler(int syncId, Inventory playerInventory, IslandGenerator data, PlayerIsland data2) {
        super(syncId, playerInventory, 5, data);

        this.server = playerInventory.player.getServer();
        this.islands = data.getPublicTeleportIslands();
        this.pages = (int) Math.ceil((islands.size())/14.0);

        this.data2 = data2;

        drawIslands();
    }

    public static void openMenu(Player player, PlayerIsland data) {
        MenuProvider fac = new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return new TextComponent("Public Islands");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int syncId, @NotNull Inventory inv, Player player) {
                AtomicReference<TeleportIslandOverviewHandler> handler = new AtomicReference<>(null);
                player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(g -> handler.set(new TeleportIslandOverviewHandler(syncId, inv, g, data)));

                return handler.get();
            }
        };
        player.openMenu(fac);
    }

    @Override
    protected boolean isRightSlot(int slot) {
        return slot == 44 || slot == 39 || slot == 41 || (slot >= 10 && slot <= 34 && slot%9 != 0 && slot%9 != 8);
    }

    @Override
    protected void fillInventoryWith(Player player) {
        for(int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack item = null;

            if (i == 44) {
                item = new ItemStack(Items.ARROW);
                item.setHoverName(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.default.back"), ChatFormatting.RED, ChatFormatting.BOLD));
            } else if(i >= 10 && i <= 34 && i%9 != 0 && i%9 != 8) {
                //BIOME So keep empty
            } else {
                item = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                item.setHoverName(new TextComponent(""));
            }

            if(item != null) setItem(i, 0, item);
        }
    }

    private void drawIslands() {
        int islandIndex = page * 21;

        for(int i = 10; i <= 34; i++) {
            if(i%9 == 0 || i%9 == 8)  continue;

            ItemStack item;
            if (islandIndex < islands.size()) {
                IslandData island = islands.get(islandIndex);
                GameProfile owner = island.getOwner(this.server);

                item = new ItemStack(Items.PLAYER_HEAD);
                item.setHoverName(ServerHelper.formattedText(owner.getName(), ChatFormatting.BOLD));
                item.getOrCreateTagElement(SkyblockAddon.MOD_ID).putUUID("islandid", data.getIslandIdByLocation(island.getCenter())); //Put biome in item NBT for click event

                CompoundTag tag = item.getOrCreateTag();
                tag.putString("SkullOwner", owner.getName());
                item.setTag(tag);

                islandIndex += 1;
            } else {
                item = new ItemStack(Items.AIR);
            }

            setItem(i, 0, item);
        }

        ItemStack prev = new ItemStack(Items.RED_BANNER);
        prev.setHoverName(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.default.previous"), ChatFormatting.RED, ChatFormatting.BOLD));
        prev.getOrCreateTagElement(SkyblockAddon.MOD_ID).putInt("page", page-1);
        if(page > 0) setItem(39, 0, prev);
        else setItem(39, 0, new ItemStack(Items.GRAY_STAINED_GLASS_PANE).setHoverName(new TextComponent("")));

        ItemStack next = new ItemStack(Items.GREEN_BANNER);
        next.setHoverName(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.default.next"), ChatFormatting.GREEN, ChatFormatting.BOLD));
        next.getOrCreateTagElement(SkyblockAddon.MOD_ID).putInt("page", page+1);
        if(page < (pages-1)) setItem(41, 0, next);
        else setItem(41, 0, new ItemStack(Items.GRAY_STAINED_GLASS_PANE).setHoverName(new TextComponent("")));

        ItemStack info = new ItemStack(Items.BOOK);
        info.setHoverName(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("guis.default.page") + " " + (this.page + 1) + "/" + this.pages));
        setItem(40, 0, info);
    }

    @Override
    protected boolean handleSlotClicked(ServerPlayer player, int index, Slot slot, int clickType) {
        switch (index) {
            case 44 -> {
                player.closeContainer();
                Objects.requireNonNull(player.getServer()).execute(() -> IslandTravelOverviewHandler.openMenu(player, data2));
                ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                return true;
            }
            case 39, 41 -> {
                if (!slot.getItem().getOrCreateTagElement(SkyblockAddon.MOD_ID).contains("page"))
                    return false; //It's not a page item;
                page = Objects.requireNonNull(slot.getItem().getTagElement(SkyblockAddon.MOD_ID)).getInt("page");
                drawIslands();
                ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                return true;
            }
            default -> {
                if (slot.getItem().isEmpty()) return false; //Empty slot clicked
                player.closeContainer();
                ServerHelper.playSongToPlayer(player, SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 1f);
                UUID islandId = Objects.requireNonNull(slot.getItem().getTagElement(SkyblockAddon.MOD_ID)).getUUID("islandid");
                this.data.getIslandById(islandId).teleport(player);
            }
        }

        return false;
    }
}
