package yorickbm.skyblockaddon.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.chunk.ChunkContent;
import yorickbm.skyblockaddon.chunk.ChunkTaskScheduler;
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

    /**
     * Primary tracker: any block placed inside an island's bounding box adds the containing
     * chunk to that island's {@code modifiedChunks} list. Covers player placement, piston push,
     * dispenser placement, and anything else that fires {@link BlockEvent.EntityPlaceEvent}.
     */
    @SubscribeEvent
    public void onBlockPlace(final BlockEvent.EntityPlaceEvent event) {
        trackModification(event.getWorld(), event.getPos());
    }

    /**
     * Primary tracker: block break by a player adds the containing chunk to the island's
     * {@code modifiedChunks} list. We intentionally do NOT remove the chunk on break - even a
     * fully-broken-back-to-air chunk needs to stay in the list so purge can verify the slot is
     * clean. Stale entries get pruned by the {@code /island admin cleanchunks} command.
     */
    @SubscribeEvent
    public void onBlockBreak(final BlockEvent.BreakEvent event) {
        trackModification(event.getWorld(), event.getPos());
    }

    /**
     * Primary tracker: fluid flow turning into a placed block (e.g. lava + water -> obsidian).
     */
    @SubscribeEvent
    public void onFluidPlaceBlock(final BlockEvent.FluidPlaceBlockEvent event) {
        trackModification(event.getWorld(), event.getPos());
    }

    private void trackModification(final LevelAccessor world, final BlockPos pos) {
        if (world.isClientSide()) return;
        if (!(world instanceof final ServerLevel serverLevel)) return;
        //Islands live in the overworld only. Block events fire for every dimension on the server
        //(Vault Hunters' vault dimension included) and getIslandByPos is purely positional, so
        //without this filter a vault placement at (x, z) gets attributed to whichever overworld
        //island's bbox contains that (x, z).
        if (serverLevel.dimension() != Level.OVERWORLD) return;
        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(pos));
        if (island == null) return;
        if (island.storeChunk(new ChunkPos(pos))) {
            LOGGER.debug("Chunk {} added to island {} via modification at {}", new ChunkPos(pos), island.getId(), pos);
        }
    }

    /**
     * Safety-net fallback for missed modification events (mod-direct setBlock, certain piston
     * interactions, etc.). Adds the chunk to the island only if it has any non-air content
     * anywhere - in a void-world overworld this is unambiguous: non-air = player or mod content.
     *
     * Purely additive - never removes. Stale entries from before event-based tracking are
     * cleaned via the {@code /island admin cleanchunks} command.
     */
    @SubscribeEvent
    public void onChunkLoad(final ChunkEvent.Load event) {
        if (!(event.getWorld() instanceof final ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD) return; //Islands live in the overworld only.

        final ChunkPos chunkPos = event.getChunk().getPos();
        serverLevel.getServer().execute(() -> processLoadedChunk(serverLevel, chunkPos));
    }

    private void processLoadedChunk(final ServerLevel serverLevel, final ChunkPos chunkPos) {
        final LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) return;

        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(chunk.getPos().getMiddleBlockPosition(155)));
        if (island == null) return;

        if (ChunkContent.hasAnyContent(chunk) && island.storeChunk(chunk)) {
            LOGGER.debug("Chunk {} added to island {} (load-time fallback, non-air content detected)", chunkPos, island.getId());
        }

        if (island.reapplyBiomeIfNeeded(chunk, serverLevel)) {
            LOGGER.debug("Reapplied biome to chunk {} for island {}", chunkPos, island.getId());
        }
    }

}
