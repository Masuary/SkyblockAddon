package yorickbm.skyblockaddon.commands;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.UsernameCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.BanNameResolution;
import yorickbm.skyblockaddon.core.util.BanNameResolution.Decision;
import yorickbm.skyblockaddon.core.util.BanNameResolution.RemoteLookup;
import yorickbm.skyblockaddon.core.util.KnownPlayerNames;
import yorickbm.skyblockaddon.core.util.PendingBanLookups;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * /island ban, unban and bans: the island's ban list.
 */
public class IslandBanCommand extends OverWorldCommandStack {
    private static final PendingBanLookups PENDING_LOOKUPS = new PendingBanLookups();
    private static final Logger LOGGER = LogManager.getLogger();

    public IslandBanCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is");
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("ban")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Cmds.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> executeBan(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity(),
                                        StringArgumentType.getString(context, "player")))
                        )
                )
                .then(Cmds.literal("unban")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Cmds.argument("player", StringArgumentType.word())
                                .suggests(this::bannedNames)
                                .executes(context -> executeUnban(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity(),
                                        StringArgumentType.getString(context, "player")))
                        )
                )
                .then(Cmds.literal("bans")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> executeList(
                                context.getSource(),
                                (ServerPlayer) context.getSource().getEntity()))
                )
        );
    }

    private CompletableFuture<Suggestions> bannedNames(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        if (!(context.getSource().getEntity() instanceof final ServerPlayer player)) return builder.buildFuture();
        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(player.getUUID());
        if (island == null) return builder.buildFuture();
        return SharedSuggestionProvider.suggest(island.getBannedPlayers().stream().map(banned -> PlayerNames.nameOf(player.server, banned)), builder);
    }

    /**
     * Online players and offline-mode players who have played on this server are banned at once. Any other name is
     * looked up at Mojang off the server thread (one lookup per player at a time; another /island ban meanwhile is refused) and the ban continues on the server
     * thread. The server's own profile cache is not used: an offline-mode server invents a profile for every failed lookup.
     */
    public int executeBan(final CommandSourceStack command, final ServerPlayer executor, final String playerName) {
        if (super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final ForgeIsland island = ownIsland(command, executor);
        if (island == null) return Command.SINGLE_SUCCESS;
        if (!IslandActionService.canManage(island, executor)) {
            command.sendFailure(IslandActionService.failureMessage(IslandActionResult.NOT_ALLOWED, ""));
            return Command.SINGLE_SUCCESS;
        }

        final ServerPlayer onlinePlayer = executor.server.getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            banResolvedPlayer(executor, onlinePlayer.getGameProfile());
            return Command.SINGLE_SUCCESS;
        }

        final Map<UUID, String> knownNames = UsernameCache.getMap();
        final Optional<UUID> knownPlayer = KnownPlayerNames.find(knownNames, playerName);
        if (!BanNameResolution.needsRemoteLookup(knownPlayer)) {
            finishLookup(executor, playerName, knownNames, knownPlayer, RemoteLookup.NOT_ASKED, null);
            return Command.SINGLE_SUCCESS;
        }
        if (knownPlayer.isEmpty() && !BanNameResolution.isPossibleName(playerName)) {
            finishLookup(executor, playerName, knownNames, knownPlayer, RemoteLookup.NOT_FOUND, null);
            return Command.SINGLE_SUCCESS;
        }

        final UUID executorId = executor.getUUID();
        final Optional<String> pendingName = PENDING_LOOKUPS.tryStart(executorId, playerName);
        if (pendingName.isPresent()) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.lookup.pending").formatted(pendingName.get())));
            return Command.SINGLE_SUCCESS;
        }
        command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.lookup").formatted(playerName))
                .withStyle(ChatFormatting.GRAY), false);
        final MinecraftServer server = executor.server;
        Util.ioPool().execute(() -> {
            final AtomicReference<GameProfile> found = new AtomicReference<>();
            final AtomicReference<Exception> failure = new AtomicReference<>();
            try {
                final GameProfileRepository repository = server.getProfileRepository();
                if (repository == null) throw new IllegalStateException("The server has no profile repository");
                repository.findProfilesByNames(new String[]{playerName}, Agent.MINECRAFT, new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(final GameProfile profile) {
                        found.set(profile);
                    }

                    @Override
                    public void onProfileLookupFailed(final GameProfile profile, final Exception exception) {
                        failure.set(exception);
                    }
                });
            } catch (final RuntimeException exception) {
                failure.set(exception);
            } finally {
                server.execute(() -> {
                    PENDING_LOOKUPS.finish(executorId);
                    final GameProfile profile = found.get();
                    final RemoteLookup outcome = profile != null ? RemoteLookup.FOUND
                            : failure.get() == null || failure.get() instanceof ProfileNotFoundException ? RemoteLookup.NOT_FOUND
                            : RemoteLookup.FAILED;
                    if (outcome == RemoteLookup.FOUND && server.getProfileCache() != null) server.getProfileCache().add(profile);
                    if (outcome == RemoteLookup.FAILED) LOGGER.warn("Looking up the player name '{}' for /island ban failed: {}", playerName, failure.get().toString());
                    finishLookup(executor, playerName, knownNames, knownPlayer, outcome, profile);
                });
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    /** A lookup still running when the server stops never reaches its server-thread step; forget it. */
    public static void clearPendingLookups() {
        PENDING_LOOKUPS.clear();
    }

    private static void finishLookup(final ServerPlayer executor, final String playerName, final Map<UUID, String> knownNames,
                                     final Optional<UUID> knownPlayer, final RemoteLookup remoteLookup, final GameProfile remoteProfile) {
        if (executor.hasDisconnected()) return;
        final Decision decision = BanNameResolution.decide(knownPlayer, remoteLookup,
                Optional.ofNullable(remoteProfile).map(GameProfile::getId));
        switch (decision.reply()) {
            case BAN -> {
                final UUID target = decision.banTarget().orElseThrow();
                final String name = remoteProfile != null && remoteProfile.getId().equals(target) ? remoteProfile.getName() : knownNames.get(target);
                banResolvedPlayer(executor, new GameProfile(target, name));
            }
            case UNKNOWN_PLAYER -> executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.unknown.player").formatted(playerName))
                    .withStyle(ChatFormatting.RED), executor.getUUID());
            case LOOKUP_FAILED -> executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.lookup.failed").formatted(playerName))
                    .withStyle(ChatFormatting.RED), executor.getUUID());
        }
    }

    private static void banResolvedPlayer(final ServerPlayer executor, final GameProfile profile) {
        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
        if (island == null) {
            executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")).withStyle(ChatFormatting.RED), executor.getUUID());
            return;
        }
        final IslandActionResult result = IslandActionService.banPlayer(executor, island, profile.getId());
        if (!result.succeeded()) {
            IslandActionService.sendFailure(executor, result, profile.getName());
            return;
        }
        executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.success").formatted(profile.getName()))
                .withStyle(ChatFormatting.GREEN), executor.getUUID());
    }

    public int executeUnban(final CommandSourceStack command, final ServerPlayer executor, final String playerName) {
        if (super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final ForgeIsland island = ownIsland(command, executor);
        if (island == null) return Command.SINGLE_SUCCESS;

        final Optional<UUID> bannedPlayer = PlayerNames.findByName(executor.server, island.getBannedPlayers(), playerName);
        if (bannedPlayer.isEmpty()) {
            command.sendFailure(IslandActionService.failureMessage(IslandActionResult.TARGET_NOT_BANNED, playerName));
            return Command.SINGLE_SUCCESS;
        }
        final IslandActionResult result = IslandActionService.unbanPlayer(executor, island, bannedPlayer.get());
        if (!result.succeeded()) {
            command.sendFailure(IslandActionService.failureMessage(result, playerName));
            return Command.SINGLE_SUCCESS;
        }
        command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.unban.success").formatted(playerName))
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
    }

    public int executeList(final CommandSourceStack command, final ServerPlayer executor) {
        if (super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final ForgeIsland island = ownIsland(command, executor);
        if (island == null) return Command.SINGLE_SUCCESS;
        if (!IslandActionService.canManage(island, executor)) {
            command.sendFailure(IslandActionService.failureMessage(IslandActionResult.NOT_ALLOWED, ""));
            return Command.SINGLE_SUCCESS;
        }

        final List<UUID> bannedPlayers = island.getBannedPlayers();
        if (bannedPlayers.isEmpty()) {
            command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.bans.none")), false);
            return Command.SINGLE_SUCCESS;
        }
        command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.bans.header").formatted(bannedPlayers.size()))
                .withStyle(ChatFormatting.GOLD), false);
        for (final UUID bannedPlayer : bannedPlayers) {
            final String name = PlayerNames.nameOf(executor.server, bannedPlayer);
            command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.bans.entry").formatted(name))
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/island unban " + name))), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static ForgeIsland ownIsland(final CommandSourceStack command, final ServerPlayer executor) {
        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
        if (island == null) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
        }
        return island;
    }
}
