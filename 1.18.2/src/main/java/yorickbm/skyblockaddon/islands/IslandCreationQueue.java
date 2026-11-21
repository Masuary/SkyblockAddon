package yorickbm.skyblockaddon.islands;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability.IslandReservation;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability.ParsedIslandStructure;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.IslandSlot;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Builds requested islands one at a time. The structure is parsed on a worker thread; slot
 * allocation, chunk tickets, block placement (a per-tick budget), registration and the teleport
 * all run on the server thread from the server tick.
 *
 * A player who logs out while waiting is dropped from the queue (cooldown refunded); an island
 * that is already being built is finished and registered, and the player finds it on return.
 * On server stop the waiting requests are dropped, a build that is placing blocks is finished
 * synchronously so the save holds either a complete registered island or nothing, and a build
 * that has not placed anything yet returns its slot.
 */
public final class IslandCreationQueue {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final IslandCreationQueue INSTANCE = new IslandCreationQueue();
    private static final TicketType<ChunkPos> ISLAND_BUILD_TICKET = TicketType.create("skyblockaddon_island_build", Comparator.comparingLong(ChunkPos::toLong));
    private static final int FULL_CHUNK_TICKET_DISTANCE = 0;
    private static final long CHUNK_LOAD_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final int STATUS_UPDATE_INTERVAL_TICKS = 10;

    private final Deque<CreationRequest> waitingRequests = new ArrayDeque<>();
    private final ExecutorService structureParser = Executors.newSingleThreadExecutor(runnable -> {
        final Thread parserThread = new Thread(runnable, "SkyblockAddon-IslandStructureParser");
        parserThread.setDaemon(true);
        return parserThread;
    });
    private ActiveBuild activeBuild;
    private int ticksUntilStatusUpdate;

    private enum BuildStage { PARSING_STRUCTURE, LOADING_CHUNKS, PLACING_BLOCKS }

    private record CreationRequest(UUID playerId, String playerName, Runnable refundCooldown, long enqueuedAtNanos) {
    }

    private static final class ActiveBuild {
        private final CreationRequest request;
        private final CompletableFuture<ParsedIslandStructure> parsedStructureFuture;
        private final long startedAtNanos = System.nanoTime();
        private BuildStage stage = BuildStage.PARSING_STRUCTURE;
        private ParsedIslandStructure parsedStructure;
        private IslandReservation reservation;
        private boolean chunkTicketsHeld;
        private boolean islandRegistered;
        private long chunkLoadStartNanos;
        private long chunkLoadMillis;
        private IslandStructurePlacement placement;

        private ActiveBuild(final CreationRequest request, final CompletableFuture<ParsedIslandStructure> parsedStructureFuture) {
            this.request = request;
            this.parsedStructureFuture = parsedStructureFuture;
        }
    }

    public static IslandCreationQueue getInstance() {
        return INSTANCE;
    }

    /**
     * @return the player's position, 1 being the island that is built now
     */
    public int enqueue(final UUID playerId, final String playerName, final Runnable refundCooldown) {
        requireServerThread();
        if (getPosition(playerId).isPresent()) {
            throw new IllegalStateException(playerName + " already has an island in the creation queue");
        }
        waitingRequests.add(new CreationRequest(playerId, playerName, refundCooldown, System.nanoTime()));
        return getPosition(playerId).orElseThrow();
    }

    public OptionalInt getPosition(final UUID playerId) {
        requireServerThread();
        int position = 1;
        if (activeBuild != null) {
            if (activeBuild.request.playerId().equals(playerId)) return OptionalInt.of(position);
            position++;
        }
        for (final CreationRequest request : waitingRequests) {
            if (request.playerId().equals(playerId)) return OptionalInt.of(position);
            position++;
        }
        return OptionalInt.empty();
    }

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        final MinecraftServer server = currentServer();

        if (activeBuild == null && !waitingRequests.isEmpty()) startNextBuild(server);
        if (activeBuild != null) advanceActiveBuild(server);

        if (--ticksUntilStatusUpdate <= 0) {
            ticksUntilStatusUpdate = STATUS_UPDATE_INTERVAL_TICKS;
            sendStatusToPlayers(server);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        final UUID playerId = event.getPlayer().getUUID();
        final Iterator<CreationRequest> waitingIterator = waitingRequests.iterator();
        while (waitingIterator.hasNext()) {
            final CreationRequest request = waitingIterator.next();
            if (!request.playerId().equals(playerId)) continue;
            waitingIterator.remove();
            request.refundCooldown().run();
            LOGGER.info("Removed {} ({}) from the island creation queue after logging out", request.playerName(), playerId);
        }
    }

    @SubscribeEvent
    public void onServerStopping(final ServerStoppingEvent event) {
        final MinecraftServer server = event.getServer();
        if (!waitingRequests.isEmpty()) {
            LOGGER.info("Server stopping: dropped {} waiting island creation request(s): {}", waitingRequests.size(),
                    waitingRequests.stream().map(CreationRequest::playerName).collect(Collectors.joining(", ")));
            waitingRequests.clear();
        }
        final ActiveBuild build = activeBuild;
        if (build == null) return;

        if (build.stage == BuildStage.PLACING_BLOCKS) {
            try {
                build.placement.placeNextBatch(overworld(server), Integer.MAX_VALUE);
                completeBuild(server, build);
            } catch (final RuntimeException exception) {
                failBuild(server, build, exception);
            }
            return;
        }

        activeBuild = null;
        releaseReservation(server, build);
        LOGGER.info("Server stopping: island creation for {} ({}) had not placed any blocks yet and was dropped",
                build.request.playerName(), build.request.playerId());
    }

    @SubscribeEvent
    public void onServerAboutToStart(final ServerAboutToStartEvent event) {
        waitingRequests.clear();
        activeBuild = null;
        ticksUntilStatusUpdate = 0;
    }

    private void startNextBuild(final MinecraftServer server) {
        final CreationRequest request = waitingRequests.poll();
        try {
            final SkyblockAddonWorldCapability capability = worldCapability(server);
            activeBuild = new ActiveBuild(request, CompletableFuture.supplyAsync(capability::parseIslandStructure, structureParser));
        } catch (final RuntimeException exception) {
            LOGGER.error("Could not start island creation for {} ({})", request.playerName(), request.playerId(), exception);
            request.refundCooldown().run();
            sendChat(server, request.playerId(), "commands.create.failure", ChatFormatting.RED);
        }
    }

    private void advanceActiveBuild(final MinecraftServer server) {
        final ActiveBuild build = activeBuild;
        try {
            switch (build.stage) {
                case PARSING_STRUCTURE -> reserveSlotWhenParsed(server, build);
                case LOADING_CHUNKS -> startPlacingWhenChunksLoaded(server, build);
                case PLACING_BLOCKS -> placeNextBatch(server, build);
            }
        } catch (final RuntimeException exception) {
            failBuild(server, build, exception);
        }
    }

    private void reserveSlotWhenParsed(final MinecraftServer server, final ActiveBuild build) {
        if (!build.parsedStructureFuture.isDone()) return;
        build.parsedStructure = build.parsedStructureFuture.join();

        if (IslandManager.getInstance().getIslandByEntityUUID(build.request.playerId()) != null) {
            activeBuild = null;
            build.request.refundCooldown().run();
            sendChat(server, build.request.playerId(), "commands.create.already", ChatFormatting.RED);
            LOGGER.info("Island creation for {} ({}) cancelled: joined an island while queued", build.request.playerName(), build.request.playerId());
            return;
        }

        build.reservation = worldCapability(server).reserveIslandLocation(build.parsedStructure);
        final ServerChunkCache chunkSource = overworld(server).getChunkSource();
        build.chunkTicketsHeld = true;
        build.reservation.chunks.forEach(chunkPos -> chunkSource.addRegionTicket(ISLAND_BUILD_TICKET, chunkPos, FULL_CHUNK_TICKET_DISTANCE, chunkPos));
        build.chunkLoadStartNanos = System.nanoTime();
        build.stage = BuildStage.LOADING_CHUNKS;
    }

    private void startPlacingWhenChunksLoaded(final MinecraftServer server, final ActiveBuild build) {
        final ServerChunkCache chunkSource = overworld(server).getChunkSource();
        final boolean allChunksLoaded = build.reservation.chunks.stream().allMatch(chunkPos -> chunkSource.getChunkNow(chunkPos.x, chunkPos.z) != null);
        if (!allChunksLoaded) {
            if (System.nanoTime() - build.chunkLoadStartNanos > CHUNK_LOAD_TIMEOUT_NANOS) {
                throw new IllegalStateException("Timed out after " + TimeUnit.NANOSECONDS.toSeconds(CHUNK_LOAD_TIMEOUT_NANOS) + "s loading "
                        + build.reservation.chunks.size() + " island chunk(s) at " + build.reservation.islandLocation);
            }
            return;
        }
        build.chunkLoadMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - build.chunkLoadStartNanos);
        build.placement = new IslandStructurePlacement(build.parsedStructure, build.reservation);
        build.stage = BuildStage.PLACING_BLOCKS;
    }

    private void placeNextBatch(final MinecraftServer server, final ActiveBuild build) {
        if (build.placement.placeNextBatch(overworld(server), SkyblockAddonConfig.getIslandBlocksPerTick())) {
            completeBuild(server, build);
        }
    }

    private void completeBuild(final MinecraftServer server, final ActiveBuild build) {
        final ServerLevel level = overworld(server);
        build.placement.sendChunksToTrackingPlayers(level);
        final Vec3i spawnPosition = build.placement.findSpawnPosition(level);
        final UUID playerId = build.request.playerId();

        if (IslandManager.getInstance().getIslandByEntityUUID(playerId) != null) {
            activeBuild = null;
            releaseReservation(server, build);
            build.request.refundCooldown().run();
            sendChat(server, playerId, "commands.create.already", ChatFormatting.RED);
            LOGGER.warn("Island creation for {} ({}) not registered: joined another island while it was built; slot {} returned for reuse",
                    build.request.playerName(), playerId, build.reservation.islandLocation);
            return;
        }

        final ForgeIsland island = new ForgeIsland(playerId, spawnPosition);
        IslandManager.getInstance().registerIsland(island, playerId);
        build.islandRegistered = true;
        activeBuild = null;
        removeChunkTickets(server, build);
        logCompletedBuild(build);

        final ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;
        if (player.level.dimension() != Level.OVERWORLD) {
            player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.ready")).withStyle(ChatFormatting.GREEN), playerId);
            return;
        }
        player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.success")).withStyle(ChatFormatting.GREEN), playerId);
        island.teleportTo(player);
    }

    private void failBuild(final MinecraftServer server, final ActiveBuild build, final Throwable failure) {
        if (activeBuild == build) activeBuild = null;
        if (build.islandRegistered) {
            LOGGER.error("Island for {} ({}) was created and registered, but welcoming the player failed", build.request.playerName(), build.request.playerId(), failure);
            return;
        }
        LOGGER.error("Island creation for {} ({}) failed", build.request.playerName(), build.request.playerId(), failure);
        releaseReservation(server, build);
        build.request.refundCooldown().run();
        sendChat(server, build.request.playerId(), "commands.create.failure", ChatFormatting.RED);
    }

    private void releaseReservation(final MinecraftServer server, final ActiveBuild build) {
        if (build.reservation == null) return;
        IslandManager.getInstance().islandSpaceReusable(build.reservation.slotCenter());
        removeChunkTickets(server, build);
    }

    private void removeChunkTickets(final MinecraftServer server, final ActiveBuild build) {
        if (!build.chunkTicketsHeld) return;
        build.chunkTicketsHeld = false;
        final ServerChunkCache chunkSource = overworld(server).getChunkSource();
        build.reservation.chunks.forEach(chunkPos -> chunkSource.removeRegionTicket(ISLAND_BUILD_TICKET, chunkPos, FULL_CHUNK_TICKET_DISTANCE, chunkPos));
    }

    private void logCompletedBuild(final ActiveBuild build) {
        final long finishedAtNanos = System.nanoTime();
        final String skippedSlots = build.reservation.skippedSlots.isEmpty() ? "none"
                : build.reservation.skippedSlots.stream().map(IslandSlot::format).collect(Collectors.joining(" "));
        LOGGER.info("Island created for {} ({}) at {}: queued {}ms, parse {}ms, {} chunk(s) loaded in {}ms, {} blocks in {} chunk(s) over {} tick(s) costing {}ms, total {}ms, skipped slots: {}",
                build.request.playerName(), build.request.playerId(), build.reservation.islandLocation,
                TimeUnit.NANOSECONDS.toMillis(build.startedAtNanos - build.request.enqueuedAtNanos()),
                build.parsedStructure.parseMillis,
                build.reservation.chunks.size(), build.chunkLoadMillis,
                build.placement.getPlacedBlockCount(), build.placement.getTouchedChunkCount(), build.placement.getBatchCount(), build.placement.getPlacementMillis(),
                TimeUnit.NANOSECONDS.toMillis(finishedAtNanos - build.request.enqueuedAtNanos()),
                skippedSlots);
    }

    private void sendStatusToPlayers(final MinecraftServer server) {
        if (activeBuild != null) {
            final String status = activeBuild.stage == BuildStage.PLACING_BLOCKS
                    ? SkyBlockAddonLanguage.getLocalizedString("commands.create.progress.building").formatted(activeBuild.placement.getProgressPercent())
                    : SkyBlockAddonLanguage.getLocalizedString("commands.create.progress.preparing");
            sendActionBar(server, activeBuild.request.playerId(), status);
        }
        int position = activeBuild == null ? 1 : 2;
        for (final CreationRequest request : waitingRequests) {
            sendActionBar(server, request.playerId(), SkyBlockAddonLanguage.getLocalizedString("commands.create.progress.queued").formatted(position));
            position++;
        }
    }

    private static void sendActionBar(final MinecraftServer server, final UUID playerId, final String message) {
        final ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) player.displayClientMessage(new TextComponent(message).withStyle(ChatFormatting.GREEN), true);
    }

    private static void sendChat(final MinecraftServer server, final UUID playerId, final String languageKey, final ChatFormatting color) {
        final ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString(languageKey)).withStyle(color), playerId);
    }

    private static SkyblockAddonWorldCapability worldCapability(final MinecraftServer server) {
        return overworld(server).getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("The overworld has no SkyblockAddon world capability"));
    }

    private static ServerLevel overworld(final MinecraftServer server) {
        return Objects.requireNonNull(server.getLevel(Level.OVERWORLD), "The server has no overworld");
    }

    private static MinecraftServer currentServer() {
        return Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "No Minecraft server is running");
    }

    private static void requireServerThread() {
        if (!currentServer().isSameThread()) {
            throw new IllegalStateException("The island creation queue may only be used on the server thread, not " + Thread.currentThread().getName());
        }
    }
}
