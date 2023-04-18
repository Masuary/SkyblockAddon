package yorickbm.skyblockaddon.events;

import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yorickbm.skyblockaddon.Main;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.islands.Permission;

/**
 * Event Source: https://forge.gemwire.uk/wiki/Events
 */
public class BlockEvents {
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        event.getPlayer().sendMessage(new TextComponent("You triggered: onBlockBreak"), event.getPlayer().getUUID());

        IslandData island = Main.CheckOnIsland(event.getPlayer());
        if(island == null) return; //We Shall do Nothing

        if(!island.hasPermission(Permission.BreakBlocks, event.getPlayer())) event.setCanceled(true);
        //Has permission so event should not be canceled
    }
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getEntity() instanceof Player player)) return;
        player.sendMessage(new TextComponent("You triggered: onBlockPlace"), player.getUUID());

        IslandData island = Main.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.hasPermission(Permission.PlaceBlocks, player)) event.setCanceled(true);
        //Has permission so event should not be canceled
    }
    @SubscribeEvent
    public void onTrampleEvent(BlockEvent.FarmlandTrampleEvent event) {
        if(!(event.getEntity() instanceof Player player)) return;
        player.sendMessage(new TextComponent("You triggered: onTrampleEvent"), player.getUUID());

        IslandData island = Main.CheckOnIsland(player);
        if(island == null) return; //We Shall do Nothing

        if(!island.hasPermission(Permission.TrampleFarmland, player)) event.setCanceled(true);
        //Has permission so event should not be canceled
    }
}
