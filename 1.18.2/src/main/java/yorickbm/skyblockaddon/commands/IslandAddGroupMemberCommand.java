package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandAddGroupMemberCommand extends OverWorldCommandStack {
    public IslandAddGroupMemberCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(
                Cmds.literal(rootLiteral)
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Cmds.literal("group")
                                .then(Cmds.argument("groupName", StringArgumentType.string())
                                        .suggests(this::groupOptions)
                                        .then(Cmds.literal("addMember")
                                                .then(Cmds.argument("player", EntityArgument.player())
                                                        .executes(context -> execute(
                                                                context.getSource(),
                                                                (ServerPlayer) context.getSource().getEntity(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "groupName")
                                                        ))
                                                )
                                        )
                                )
                        )
        );
    }

    private CompletableFuture<Suggestions> groupOptions(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        final ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
        if(player == null) return builder.buildFuture();

        final Island island = IslandManager.getInstance().getIslandByEntityUUID(player.getUUID());
        if (island == null) {
            return builder.buildFuture();
        }

        island.getGroups().forEach(g -> {
            if(g.getId().equals(SkyblockAddonCore.MOD_UUID) || g.getId().equals(SkyblockAddonCore.MOD_UUID2)) return; //Skip basic groups
            builder.suggest("\"" + g.getName() + "\"");
        });

        return builder.buildFuture();
    }

    public int execute(final CommandSourceStack command, final ServerPlayer executor, final ServerPlayer target, final String groupName) {
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final Island island = IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
        if (island == null) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
            return Command.SINGLE_SUCCESS;
        }

        final Optional<UUID> groupId = island.getGroupByName(groupName);
        if(groupId.isEmpty() || !island.hasGroup(groupId.get())) {
            command.sendFailure(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("commands.group.not.found"), groupId)));
            return Command.SINGLE_SUCCESS;
        }

        final IslandGroup group = island.getGroup(groupId.get());
        if(group.getId().equals(SkyblockAddonCore.MOD_UUID) || group.getId().equals(SkyblockAddonCore.MOD_UUID2)) {
            command.sendFailure(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("commands.group.not.found"), groupId)));
            return Command.SINGLE_SUCCESS;
        }
        group.addMember(target.getUUID());

        command.sendSuccess(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("commands.group.added.member"), target.getDisplayName().getString().trim(), group.getName())).withStyle(ChatFormatting.GREEN), false);


        return Command.SINGLE_SUCCESS;
    }
}
