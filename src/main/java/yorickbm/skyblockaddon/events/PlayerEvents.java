package yorickbm.skyblockaddon.events;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import yorickbm.skyblockaddon.SkyblockAddon;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.islands.Permissions;
import yorickbm.skyblockaddon.util.LanguageFile;
import yorickbm.skyblockaddon.util.ModIntegrationHandler;
import yorickbm.skyblockaddon.util.MouseButton;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Random;

/**
 * Event Source: https://forge.gemwire.uk/wiki/Events
 *
 * Right CLick -> Use/Place Block
 * Left Click -> Attack/Destroy Block
 */
public class PlayerEvents {
    @SubscribeEvent
    public void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        IslandData island = SkyblockAddon.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.UseEnderpearl, player.getUUID()).isAllowed()) {
            player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
            event.setCanceled(true);
        }
        //Has permission so event should not be canceled
    }
    @SubscribeEvent
    public void onChorusFruit(EntityTeleportEvent.ChorusFruit event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        IslandData island = SkyblockAddon.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.UseChorusfruit, player.getUUID()).isAllowed()) {
            player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
            event.setCanceled(true);
        }
        //Has permission so event should not be canceled
    }

    @SubscribeEvent
    public void onPlayerSleepInBed(PlayerSleepInBedEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        IslandData island = SkyblockAddon.CheckOnIsland(event.getPlayer());
        if(island == null) return; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.UseBed, player.getUUID()).isAllowed()) {
            event.getPlayer().displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
        //Has permission so event should not be canceled
    }

    @SubscribeEvent
    public void onPlayerXP(PlayerXpEvent.PickupXp event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        IslandData island = SkyblockAddon.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.CollectXP, player.getUUID()).isAllowed()) {
            player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
            event.setCanceled(true);
        }
        //Has permission so event should not be canceled
    }

    @SubscribeEvent
    public void onUseBucket(FillBucketEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        IslandData island = SkyblockAddon.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.UseBucket, player.getUUID()).isAllowed()) {
            player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
            event.setCanceled(true);
        }
        //Has permission so event should not be canceled
    }

    @SubscribeEvent
    public void onBonemeal(BonemealEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        IslandData island = SkyblockAddon.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.UseBonemeal, player.getUUID()).isAllowed()) {
            player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
            event.setCanceled(true);
        }
        //Has permission so event should not be canceled
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        IslandData island = SkyblockAddon.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.InteractWithGroundItems, player.getUUID()).isAllowed()) {
            player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
            event.setCanceled(true);
        }
        //Has permission so event should not be canceled
    }
    @SubscribeEvent
    public void onItemDrop(ItemTossEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        IslandData island = SkyblockAddon.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.InteractWithGroundItems, player.getUUID()).isAllowed()) {
            player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
            event.setCanceled(true);
        }
        //Has permission so event should not be canceled
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getPlayer();
        Entity entity = event.getTarget();

        if(entity instanceof Villager villager && player.isShiftKeyDown() && ModList.get().isLoaded("easy_villagers")) {
            IslandData island = SkyblockAddon.CheckOnIsland(player);
            if(island == null) return; //We Shall do Nothing

            if(!island.isOwner(player.getUUID()) && !island.getPermission(Permissions.InteractWithBlocks, player.getUUID()).isAllowed()) {
                player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);
                event.setCancellationResult(InteractionResult.FAIL);

                //Clone villager to prevent Easy Villagers pickup
                Villager clone = new Villager(EntityType.VILLAGER, villager.level);
                clone.deserializeNBT(villager.serializeNBT());
                clone.setUUID(Mth.createInsecureUUID(new Random()));

                //Delete clicked villager, spawn its clone
                villager.discard();
                player.getLevel().addFreshEntity(clone);

                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onBlockInteractRBlock(PlayerInteractEvent.RightClickBlock event) {
       if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        if(player.isSecondaryUseActive() && !event.getItemStack().isEmpty()) return; //Secondary use is allowed

        event.setCanceled(HandleBlockClick(player, event.getPos(), MouseButton.RightClick, event.getItemStack()));
    }
    @SubscribeEvent
    public void onBlockInteractREmpty(PlayerInteractEvent.RightClickEmpty event) {
       if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        event.setCanceled(HandleBlockClick(player, event.getPos(), MouseButton.RightClick, event.getItemStack()));
    }
    @SubscribeEvent
    public void onBlockInteractLBlock(PlayerInteractEvent.LeftClickBlock event) {
       if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        event.setCanceled(HandleBlockClick(player, event.getPos(), MouseButton.LeftClick, event.getItemStack()));
    }
    @SubscribeEvent
    public void onBlockInteractLEmpty(PlayerInteractEvent.LeftClickEmpty event) {
       if(!(event.getEntity() instanceof ServerPlayer player) || event.getEntity() instanceof FakePlayer) return; //Allow fake players

        event.setCanceled(HandleBlockClick(player, event.getPos(), MouseButton.LeftClick, event.getItemStack()));
    }

    private boolean HandleBlockClick(ServerPlayer player, BlockPos posClicked, MouseButton button, ItemStack triggerItem) {
        BlockEntity blockEntity = player.getLevel().getBlockEntity(posClicked); //Get block entity
        Block block = player.getLevel().getBlockState(posClicked).getBlock(); //Get block

        Permissions permission = blockEntity != null ? ModIntegrationHandler.getPermissionForBlockEntity(blockEntity) : ModIntegrationHandler.getPermissionForBlock(block);
        if(permission == null) return false; //Block type is not blocked by our permissions to be clicked on

        IslandData island = SkyblockAddon.CheckOnIsland(player);
        if(island == null) return false; //We Shall do Nothing

        if(!island.isOwner(player.getUUID()) && !island.getPermission(permission, player.getUUID())
            .isAllowed(block.asItem())) {
            player.displayClientMessage(ServerHelper.formattedText(LanguageFile.getForKey("toolbar.overlay.nothere"), ChatFormatting.DARK_RED), true);

            //Update doors
            if (block instanceof DoorBlock) {
                DoubleBlockHalf half = player.getLevel().getBlockState(posClicked).getValue(DoorBlock.HALF);
                if (half == DoubleBlockHalf.LOWER) {
                    BlockState other = player.getLevel().getBlockState(posClicked.above());
                    ServerHelper.SendPacket(player, new ClientboundBlockUpdatePacket(posClicked.above(), other));
                } else {
                    BlockState other = player.getLevel().getBlockState(posClicked.below());
                    ServerHelper.SendPacket(player, new ClientboundBlockUpdatePacket(posClicked.below(), other));
                }
            }

            return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if(!SkyblockAddon.islandUIIds.contains(event.getContainer().containerId)) return; //Its not an island GUI so we ignore event

        //Remove all items containing skyblockaddon tag
        event.getPlayer().inventoryMenu.slots.forEach(slot -> {
            if(slot.getItem().getTagElement(SkyblockAddon.MOD_ID) != null) event.getPlayer().inventoryMenu.setItem(slot.index, 0, ItemStack.EMPTY);
        });
    }


}
