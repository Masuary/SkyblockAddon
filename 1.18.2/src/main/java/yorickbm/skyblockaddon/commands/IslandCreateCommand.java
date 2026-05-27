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
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class IslandCreateCommand extends OverWorldCommandStack {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, Long> cooldowns = Collections.synchronizedMap(new WeakHashMap<>());

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
        if(this.hasActiveCooldown(executor)) {
            command.sendFailure(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("commands.create.cooldown"), (this.getCooldownSecondsLeft(executor) + "s"))));
            return Command.SINGLE_SUCCESS;
        };

        command.getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            final Island island = IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
            if(island != null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.already")));
                return;
            }

            executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.generating")).withStyle(ChatFormatting.GREEN), executor.getUUID());

            //Reserve the cooldown slot now so a double-click during gen is rejected by hasActiveCooldown().
            //Refunded below if the parse or placement fails.
            cooldowns.put(executor.getUUID(), System.currentTimeMillis());

            final MinecraftServer server = command.getServer();
            final ServerLevel level = command.getLevel();
            final UUID executorId = executor.getUUID();
            final String executorName = executor.getGameProfile().getName();
            final long createStartNanos = System.nanoTime();

            final Thread asyncIslandGen = new Thread(() -> {
                //Step 1 (off-thread): parse structure NBT. No world or singleton state touched.
                final SkyblockAddonWorldCapability.ParsedIslandStructure parsed;
                try {
                    parsed = cap.parseIslandStructure();
                } catch (final Exception parseException) {
                    LOGGER.error("Failed to parse island structure for {}", executorId, parseException);
                    server.execute(() -> {
                        cooldowns.remove(executorId);
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                }

                //Step 2 (server thread): race-check + reserve location + compute chunks to preload.
                final CompletableFuture<SkyblockAddonWorldCapability.IslandReservation> reservationFuture = new CompletableFuture<>();
                server.execute(() -> {
                    try {
                        final Island racingIsland = IslandManager.getInstance().getIslandByEntityUUID(executorId);
                        if(racingIsland != null) {
                            reservationFuture.complete(null); //sentinel: racing
                            return;
                        }
                        reservationFuture.complete(cap.reserveIslandLocation(parsed));
                    } catch (final Throwable t) {
                        reservationFuture.completeExceptionally(t);
                    }
                });

                final SkyblockAddonWorldCapability.IslandReservation reservation;
                try {
                    reservation = reservationFuture.get(30, TimeUnit.SECONDS);
                } catch (final TimeoutException timeout) {
                    LOGGER.error("Timed out waiting for island reservation for {}", executorId);
                    server.execute(() -> {
                        cooldowns.remove(executorId);
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                } catch (final Exception reservationException) {
                    LOGGER.error("Failed to reserve island location for {}", executorId, reservationException);
                    server.execute(() -> {
                        cooldowns.remove(executorId);
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                }
                if(reservation == null) {
                    server.execute(() -> {
                        cooldowns.remove(executorId);
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.already")));
                    });
                    return;
                }

                //Step 3 (off-thread): pre-load every chunk the structure will touch. The actual
                //worldgen work runs on Minecraft's worldgen worker pool; the server tick is only
                //touched briefly for the FULL-status upgrades. Without this step, level.getChunk
                //in placeReservedIsland would block the server tick for ~1.5s in unexplored areas.
                final long chunkLoadStart = System.nanoTime();
                try {
                    final CompletableFuture<?>[] chunkFutures = reservation.chunks.stream()
                            .map(cp -> level.getChunkSource().getChunkFuture(cp.x, cp.z, net.minecraft.world.level.chunk.ChunkStatus.FULL, true))
                            .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(chunkFutures).get(60, TimeUnit.SECONDS);
                } catch (final TimeoutException timeout) {
                    LOGGER.error("Timed out pre-loading island chunks for {} ({}s)", executorId, 60);
                    server.execute(() -> {
                        cooldowns.remove(executorId);
                        IslandManager.getInstance().islandSpaceReusable(yorickbm.skyblockaddon.util.ForgeConverter.ForgeToInternalVec3i(reservation.islandLocation));
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                    });
                    return;
                } catch (final Exception chunkException) {
                    LOGGER.error("Failed to pre-load island chunks for {}", executorId, chunkException);
                    server.execute(() -> {
                        cooldowns.remove(executorId);
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
                        vec = cap.placeReservedIsland(level, parsed, reservation);
                    } catch (final Exception placementException) {
                        LOGGER.error("Failed to place island for {}", executorId, placementException);
                        cooldowns.remove(executorId);
                        IslandManager.getInstance().islandSpaceReusable(yorickbm.skyblockaddon.util.ForgeConverter.ForgeToInternalVec3i(reservation.islandLocation));
                        command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.failure")));
                        return;
                    }

                    final ForgeIsland newIsland = new ForgeIsland(executorId, vec);
                    IslandManager.getInstance().registerIsland(newIsland, executorId);

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

    private boolean hasActiveCooldown(final ServerPlayer player) {
        final UUID uuid = player.getUUID();
        final long currentTime = System.currentTimeMillis();

        final String cooldownStr = SkyblockAddonConfig.getForKey("island.create.cooldown");
        long cooldownSeconds;
        try {
            cooldownSeconds = Long.parseLong(cooldownStr);
        } catch (NumberFormatException e) {
            cooldownSeconds = SkyblockAddonCore.DEFAULT_CREATE_COOLDOWN; //Default value
        }

        final long cooldownTimeMs = cooldownSeconds * 1000;

        final Long lastUsed = cooldowns.get(uuid);
        if (lastUsed != null && (currentTime - lastUsed) < cooldownTimeMs) {
            return true;
        }
        return false;
    }

    private long getCooldownSecondsLeft(final ServerPlayer player) {
        final UUID uuid = player.getUUID();

        final Long lastUsed = cooldowns.get(uuid);
        if (lastUsed == null) {
            return 0;
        }

        final long currentTime = System.currentTimeMillis();

        final String cooldownStr = SkyblockAddonConfig.getForKey("island.create.cooldown");
        long cooldownSeconds;
        try {
            cooldownSeconds = Long.parseLong(cooldownStr);
        } catch (final NumberFormatException e) {
            cooldownSeconds = SkyblockAddonCore.DEFAULT_CREATE_COOLDOWN;
        }

        final long cooldownTimeMs = cooldownSeconds * 1000;
        final long timeLeftMs = (lastUsed + cooldownTimeMs) - currentTime;

        // Round up
        return Math.max(0, (timeLeftMs + 999) / 1000);
    }
}
