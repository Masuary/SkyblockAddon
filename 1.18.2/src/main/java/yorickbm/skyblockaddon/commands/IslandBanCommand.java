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
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /island ban, unban and bans: the island's ban list.
 */
public class IslandBanCommand extends OverWorldCommandStack {
    public IslandBanCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is");
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("ban")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Cmds.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> executeBan(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity(),
                                        GameProfileArgument.getGameProfiles(context, "player")))
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

    public int executeBan(final CommandSourceStack command, final ServerPlayer executor, final Collection<GameProfile> profiles) {
        if (super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final ForgeIsland island = ownIsland(command, executor);
        if (island == null) return Command.SINGLE_SUCCESS;
        if (profiles.size() != 1) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.target.single")));
            return Command.SINGLE_SUCCESS;
        }

        final GameProfile profile = profiles.iterator().next();
        final IslandActionResult result = IslandActionService.banPlayer(executor, island, profile.getId());
        if (!result.succeeded()) {
            command.sendFailure(IslandActionService.failureMessage(result, profile.getName()));
            return Command.SINGLE_SUCCESS;
        }
        command.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.success").formatted(profile.getName()))
                .withStyle(ChatFormatting.GREEN), false);
        return Command.SINGLE_SUCCESS;
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
