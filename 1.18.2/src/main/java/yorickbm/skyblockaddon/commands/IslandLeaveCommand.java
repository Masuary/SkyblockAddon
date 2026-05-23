package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.FunctionRegistry;

import java.util.UUID;

public class IslandLeaveCommand extends OverWorldCommandStack {
    public IslandLeaveCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("leave")
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
            final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByEntityUUID(executor.getUUID());
            if (island == null) {
                command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island")));
                return;
            }

            //Undo leave function
            final UUID functionKey = UUID.randomUUID();
            FunctionRegistry.registerFunction(functionKey, (e) -> {
                if(super.execute(command, e) == 0) return true;

                executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.leave.undo")).withStyle(ChatFormatting.GREEN), executor.getUUID());
                if(!island.addMember(e, e.getUUID())) {
                    e.sendMessage(
                        new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.leave.undo.failure").formatted(island.getId()))
                        .withStyle(ChatFormatting.RED)
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, island.getId().toString())))
                    , e.getUUID());
                }

                return true;
            }, 15);

            island.kickMember(executor, executor.getUUID());

            command.sendSuccess(
                new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.leave"))
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, FunctionRegistry.getCommand(functionKey))))
                , false);
        });

        return Command.SINGLE_SUCCESS;
    }

}
