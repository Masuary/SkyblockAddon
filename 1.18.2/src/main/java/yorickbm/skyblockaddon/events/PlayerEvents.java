package yorickbm.skyblockaddon.events;

import iskallia.vault.entity.entity.DollMiniMeEntity;
import iskallia.vault.entity.entity.SpiritEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.ForgeConverter;

public class PlayerEvents {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onVoidFall(final LivingDamageEvent event) {
        if(event.getSource().equals(DamageSource.OUT_OF_WORLD)) {
            final Entity entity = event.getEntity();

            if(entity instanceof ServerPlayer || entity instanceof DollMiniMeEntity || entity instanceof SpiritEntity) {
                if(entity.getLevel().dimension() != Level.OVERWORLD) return; //Ignore non overworld events

                final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(entity.getOnPos()));
                if(island == null) return;
                event.setCanceled(true); //Cancel damage

                entity.resetFallDistance();
                island.teleportTo(entity);
                entity.resetFallDistance();
            }
        }
    }
}
