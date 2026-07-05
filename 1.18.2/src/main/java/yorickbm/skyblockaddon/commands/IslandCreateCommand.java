package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandStructurePlacer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class IslandCreateCommand extends OverWorldCommandStack {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final Set<UUID> creationsInProgress = ConcurrentHashMap.newKeySet();

    public IslandCreateCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // alias
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(
                Cmds.literal(rootLiteral)
                        .then(Cmds.literal("create")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> execute(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity()))
                        )
        );
    }

    @Override
    public int execute(final CommandSourceStack command, final ServerPlayer executor) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;
        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final Island island = IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
            if(island != null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.already")));
                return;
            }

            final long reservationTimestamp = System.currentTimeMillis();
            if (!tryReserveCreation(executor.getUUID(), reservationTimestamp)) {
                command.sendFailure(new TextComponent(String.format(
                        SkyBlockAddonLanguage.getLocalizedString("commands.create.cooldown"),
                        this.getCooldownSecondsLeft(executor) + "s"
                )));
                return;
            }

            executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.generating")).withStyle(ChatFormatting.GREEN), executor.getUUID());

            final MinecraftServer server = command.getServer();
            final ServerLevel level = command.getLevel();
            final UUID executorId = executor.getUUID();
            final String executorName = executor.getGameProfile().getName();
            final long createStartNanos = System.nanoTime();

            final IslandStructurePlacer placer = new IslandStructurePlacer(server);
            final Thread asyncIslandGen = new Thread(() -> {
                //Step 1 (off-thread): parse structure NBT. No world or singleton state touched.
                final IslandStructurePlacer.ParsedIslandStructure parsed;
                try {
                    parsed = placer.parseIslandStructure();
                } catch (final Exception parseException) {
                    LOGGER.error("Failed to parse island structure for {}", executorId, parseException);
                    server.execute(() -> {
                        releaseCreationReservation(executorId, reservationTimestamp);
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                }

                //Step 2 (server thread): race-check + reserve location + compute chunks to preload.
                final CompletableFuture<IslandStructurePlacer.IslandReservation> reservationFuture = new CompletableFuture<>();
                server.execute(() -> {
                    try {
                        final Island racingIsland = IslandManager.getInstance().getIslandByEntityUUID(executorId);
                        if(racingIsland != null) {
                            reservationFuture.complete(null); //sentinel: racing
                            return;
                        }
                        reservationFuture.complete(placer.reserveIslandLocation(parsed));
                    } catch (final Throwable t) {
                        reservationFuture.completeExceptionally(t);
                    }
                });

                final IslandStructurePlacer.IslandReservation reservation;
                try {
                    reservation = reservationFuture.get(30, TimeUnit.SECONDS);
                } catch (final TimeoutException timeout) {
                    LOGGER.error("Timed out waiting for island reservation for {}", executorId);
                    server.execute(() -> {
                        releaseCreationReservation(executorId, reservationTimestamp);
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                } catch (final Exception reservationException) {
                    LOGGER.error("Failed to reserve island location for {}", executorId, reservationException);
                    server.execute(() -> {
                        releaseCreationReservation(executorId, reservationTimestamp);
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                }
                if(reservation == null) {
                    server.execute(() -> {
                        releaseCreationReservation(executorId, reservationTimestamp);
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.already")));
                    });
                    return;
                }

                //Step 3: initiate all Minecraft chunk work on the server thread, then wait for
                //the returned futures off-thread. Without preload, placement can block the tick
                //for ~1.5s in unexplored areas.
                final long chunkLoadStart = System.nanoTime();
                final CompletableFuture<Void> chunkPreloadFuture = new CompletableFuture<>();
                server.execute(() -> {
                    try {
                        final CompletableFuture<?>[] chunkFutures = reservation.chunks.stream()
                                .map(cp -> level.getChunkSource().getChunkFuture(
                                        cp.x,
                                        cp.z,
                                        net.minecraft.world.level.chunk.ChunkStatus.FULL,
                                        true
                                ))
                                .toArray(CompletableFuture[]::new);
                        CompletableFuture.allOf(chunkFutures).whenComplete((ignored, throwable) -> {
                            if (throwable == null) chunkPreloadFuture.complete(null);
                            else chunkPreloadFuture.completeExceptionally(throwable);
                        });
                    } catch (final Throwable throwable) {
                        chunkPreloadFuture.completeExceptionally(throwable);
                    }
                });
                try {
                    chunkPreloadFuture.get(60, TimeUnit.SECONDS);
                } catch (final TimeoutException timeout) {
                    LOGGER.error("Timed out pre-loading island chunks for {} ({}s)", executorId, 60);
                    server.execute(() -> {
                        releaseCreationReservation(executorId, reservationTimestamp);
                        IslandManager.getInstance().islandSpaceReusable(yorickbm.skyblockaddon.util.ForgeConverter.ForgeToInternalVec3i(reservation.islandLocation));
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                } catch (final Exception chunkException) {
                    LOGGER.error("Failed to pre-load island chunks for {}", executorId, chunkException);
                    server.execute(() -> {
                        releaseCreationReservation(executorId, reservationTimestamp);
                        IslandManager.getInstance().islandSpaceReusable(yorickbm.skyblockaddon.util.ForgeConverter.ForgeToInternalVec3i(reservation.islandLocation));
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                }
                final long chunkLoadMs = (System.nanoTime() - chunkLoadStart) / 1_000_000L;
                LOGGER.info("Island gen: preloaded {} chunk(s) for {} in {}ms (off-thread)", reservation.chunks.size(), executorId, chunkLoadMs);

                //Step 4 (server thread): place blocks, register, teleport. Chunks are already
                //loaded so this is just setBlockState calls + packet sends.
                server.execute(() -> {
                    final Vec3i vec;
                    try {
                        vec = placer.placeReservedIsland(level, parsed, reservation);
                    } catch (final Exception placementException) {
                        LOGGER.error("Failed to place island for {}", executorId, placementException);
                        releaseCreationReservation(executorId, reservationTimestamp);
                        IslandManager.getInstance().islandSpaceReusable(yorickbm.skyblockaddon.util.ForgeConverter.ForgeToInternalVec3i(reservation.islandLocation));
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                        return;
                    }

                    final ForgeIsland newIsland;
                    try {
                        newIsland = new ForgeIsland(executorId, vec);
                        IslandManager.getInstance().registerIsland(newIsland, executorId);
                        creationsInProgress.remove(executorId);
                    } catch (final RuntimeException registrationException) {
                        LOGGER.error("Failed to register generated island for {}", executorId, registrationException);
                        releaseCreationReservation(executorId, reservationTimestamp);
                        IslandManager.getInstance().islandSpaceReusable(
                                yorickbm.skyblockaddon.util.ForgeConverter.ForgeToInternalVec3i(reservation.islandLocation)
                        );
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                        return;
                    }

                    executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.success")).withStyle(ChatFormatting.GREEN), executorId);
                    newIsland.teleportTo(executor);

                    final long totalMs = (System.nanoTime() - createStartNanos) / 1_000_000L;
                    LOGGER.info("Island gen: /island create for {} ({}) completed in {}ms total", executorName, executorId, totalMs);
                });
            }, "SkyblockAddon-IslandGen-" + executorId);
            asyncIslandGen.setDaemon(true);
            asyncIslandGen.start();
        });

        return Command.SINGLE_SUCCESS;
    }

    private boolean tryReserveCreation(final UUID playerId, final long now) {
        if (!creationsInProgress.add(playerId)) return false;
        final long cooldownTimeMs = getCooldownMilliseconds();
        while (true) {
            final Long existing = cooldowns.get(playerId);
            if (existing == null) {
                if (cooldowns.putIfAbsent(playerId, now) == null) return true;
                continue;
            }
            if (now - existing < cooldownTimeMs) {
                creationsInProgress.remove(playerId);
                return false;
            }
            if (cooldowns.replace(playerId, existing, now)) return true;
        }
    }

    private void releaseCreationReservation(final UUID playerId, final long reservationTimestamp) {
        creationsInProgress.remove(playerId);
        cooldowns.remove(playerId, reservationTimestamp);
    }

    private long getCooldownSecondsLeft(final ServerPlayer player) {
        final UUID uuid = player.getUUID();

        final Long lastUsed = cooldowns.get(uuid);
        if (lastUsed == null) {
            return 0;
        }

        final long currentTime = System.currentTimeMillis();

        final long cooldownTimeMs = getCooldownMilliseconds();
        final long timeLeftMs = (lastUsed + cooldownTimeMs) - currentTime;

        // Round up
        return Math.max(0, (timeLeftMs + 999) / 1000);
    }

    private long getCooldownMilliseconds() {
        final String configuredCooldown = SkyblockAddonConfig.getForKey("island.create.cooldown");
        try {
            return Math.max(0L, Long.parseLong(configuredCooldown)) * 1000L;
        } catch (final NumberFormatException exception) {
            return SkyblockAddonCore.DEFAULT_CREATE_COOLDOWN * 1000L;
        }
    }
}
