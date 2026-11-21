package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.OwnershipTransferConfirmation;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandTransferCommand extends OverWorldCommandStack {
    public IslandTransferCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is");
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("transfer")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Cmds.argument("member", StringArgumentType.word())
                                .suggests(this::memberNames)
                                .executes(context -> execute(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity(),
                                        StringArgumentType.getString(context, "member")))
                        )
                )
        );
    }

    private CompletableFuture<Suggestions> memberNames(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        if (!(context.getSource().getEntity() instanceof final ServerPlayer player)) return builder.buildFuture();
        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(player.getUUID());
        if (island == null || !island.isOwner(player.getUUID())) return builder.buildFuture();
        return SharedSuggestionProvider.suggest(island.getMembers().stream().map(member -> PlayerNames.nameOf(player.server, member)), builder);
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final String memberName) {
        if (super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
        if (island == null) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
            return Command.SINGLE_SUCCESS;
        }

        final Optional<UUID> newOwner = PlayerNames.findByName(executor.server, island.getMembers(), memberName);
        if (newOwner.isEmpty()) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.transfer.unknown").formatted(memberName)));
            return Command.SINGLE_SUCCESS;
        }

        OwnershipTransferConfirmation.offer(executor, island, newOwner.get());
        return Command.SINGLE_SUCCESS;
    }
}
