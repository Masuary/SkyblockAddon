package yorickbm.skyblockaddon.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /island ban, unban and bans: the island's ban list.
 */
public class IslandBanCommand extends OverWorldCommandStack {
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
     * Online players are banned at once. Any other name is resolved through the server's profile cache off the server
     * thread (an unknown name means a Mojang lookup) and the ban continues on the server thread.
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

        command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.lookup").formatted(playerName))
                .withStyle(ChatFormatting.GRAY), false);
        executor.server.getProfileCache().getAsync(playerName, lookupResult -> {
            if (executor.hasDisconnected()) return;
            if (lookupResult == null) {
                LOGGER.warn("Looking up the player name '{}' for /island ban failed", playerName);
                executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.lookup.failed").formatted(playerName))
                        .withStyle(ChatFormatting.RED), executor.getUUID());
                return;
            }
            lookupResult.ifPresentOrElse(profile -> banResolvedPlayer(executor, profile),
                    () -> executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.unknown.player").formatted(playerName))
                            .withStyle(ChatFormatting.RED), executor.getUUID()));
        });
        return Command.SINGLE_SUCCESS;
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
