package yorickbm.skyblockaddon.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.chunk.ChunkTaskScheduler;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.ForgeConverter;

public class ChunkEvents {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ChunkTaskScheduler.forwardTicks();
        }
    }

    @SubscribeEvent
    public void onChunkLoad(final ChunkEvent.Load event) {
        if (!(event.getWorld() instanceof final ServerLevel serverLevel)) return;

        final ChunkAccess chunk = (ChunkAccess) event.getChunk();
        final ChunkPos pos = chunk.getPos();

        final ForgeIsland island = (ForgeIsland)IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(chunk.getPos().getMiddleBlockPosition(155)));
        if(island == null) return;

        if(island.storeChunk(chunk)) LOGGER.debug("Chunk {} added to island {}", pos, island.getId());

        if(island.reapplyBiomeIfNeeded(chunk, serverLevel)) LOGGER.debug("Reapplied biome to chunk {} for island {}", pos, island.getId());
    }
}
