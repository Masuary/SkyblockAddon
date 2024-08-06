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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class JoinIslandOverviewHandler extends ServerOnlyHandler<IslandGenerator> {

    private final List<IslandData> islands;
    private final int pages;
    private int page = 0;
    private final MinecraftServer server;

    private final PlayerIsland data2;

    protected JoinIslandOverviewHandler(int syncId, Inventory playerInventory, IslandGenerator data, PlayerIsland data2) {
        super(syncId, playerInventory, 5, data);

        this.server = playerInventory.player.getServer();
        this.islands = new ArrayList<>();
        this.pages = (int) Math.ceil((0)/14.0);

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
                AtomicReference<JoinIslandOverviewHandler> handler = new AtomicReference<>(null);
                player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(g -> handler.set(new JoinIslandOverviewHandler(syncId, inv, g, data)));

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
                item.setHoverName(ServerHelper.formattedText("Back", ChatFormatting.RED, ChatFormatting.BOLD));
            } else if(i < 10 || i > 34 || i%9 == 0 || i%9 == 8) {
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
                item.getOrCreateTagElement("skyblockaddon").putString("islandid", data.getIslandIdByLocation(island.getCenter()).toString()); //Put biome in item NBT for click event

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
        prev.setHoverName(ServerHelper.formattedText("Previous", ChatFormatting.RED, ChatFormatting.BOLD));
        prev.getOrCreateTagElement("skyblockaddon").putInt("page", page-1);
        if(page > 0) setItem(39, 0, prev);
        else setItem(39, 0, new ItemStack(Items.GRAY_STAINED_GLASS_PANE).setHoverName(new TextComponent("")));

        ItemStack next = new ItemStack(Items.GREEN_BANNER);
        next.setHoverName(ServerHelper.formattedText("Next", ChatFormatting.GREEN, ChatFormatting.BOLD));
        next.getOrCreateTagElement("skyblockaddon").putInt("page", page+1);
        if(page < (pages-1)) setItem(41, 0, next);
        else setItem(41, 0, new ItemStack(Items.GRAY_STAINED_GLASS_PANE).setHoverName(new TextComponent("")));

        ItemStack info = new ItemStack(Items.BOOK);
        info.setHoverName(ServerHelper.formattedText("Page " + (this.page + 1) + "/" + this.pages));
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
                if (!slot.getItem().getOrCreateTagElement("skyblockaddon").contains("page"))
                    return false; //It's not a page item;
                page = Objects.requireNonNull(slot.getItem().getTagElement("skyblockaddon")).getInt("page");
                drawIslands();
                ServerHelper.playSongToPlayer(player, SoundEvents.UI_BUTTON_CLICK, SkyblockAddon.UI_SOUND_VOL, 1f);
                return true;
            }
            default -> {
                if (slot.getItem().isEmpty()) return false; //Empty slot clicked
                player.closeContainer();
                ServerHelper.playSongToPlayer(player, SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 1f);
                UUID islandId = UUID.fromString(Objects.requireNonNull(slot.getItem().getTagElement("skyblockaddon")).getString("islandid"));
                IslandData island = this.data.getIslandById(islandId);
                island.addIslandMember(player.getUUID());
                data2.setIsland(islandId);
                player.sendMessage(new TextComponent(SkyblockAddonLanguageConfig.getForKey("guis.island.join").formatted(island.getOwner(player.getServer()).getName())).withStyle(ChatFormatting.GREEN), player.getUUID());
                island.teleport(player);
            }
        }

        return false;
    }
}
