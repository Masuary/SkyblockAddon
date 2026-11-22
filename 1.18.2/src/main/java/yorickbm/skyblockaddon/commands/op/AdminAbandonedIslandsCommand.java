package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.AbandonedIslandQuery;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGridSlot;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.PurgeEligibility;
import yorickbm.skyblockaddon.core.util.ResultPage;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.List;

public class AdminAbandonedIslandsCommand {
    private static final int ISLANDS_PER_PAGE = 10;

    public AdminAbandonedIslandsCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.adminLiteral()
                        .then(Cmds.literal("abandoned")
                                .requires(Cmds.permissionLevel(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> listAbandonedIslands(context.getSource(), 1))
                                .then(Cmds.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> listAbandonedIslands(context.getSource(), IntegerArgumentType.getInteger(context, "page"))))
                        )
                )
        );
    }

    private int listAbandonedIslands(final CommandSourceStack source, final int requestedPageNumber) {
        final List<Island> abandonedIslands = AbandonedIslandQuery.findAbandonedIslands(IslandManager.getInstance().getIslands());
        if (abandonedIslands.isEmpty()) {
            source.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.abandoned.none"))
                    .withStyle(ChatFormatting.GREEN), false);
            return Command.SINGLE_SUCCESS;
        }

        final ResultPage<Island> page = ResultPage.of(abandonedIslands, requestedPageNumber, ISLANDS_PER_PAGE);
        source.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.abandoned.header")
                .formatted(page.totalEntries(), page.pageNumber(), page.pageCount()))
                .withStyle(ChatFormatting.GOLD), false);
        page.entries().forEach(island -> source.sendSuccess(describeIsland(island), false));

        if (page.pageNumber() < page.pageCount()) {
            final String nextPageCommand = "/island admin abandoned " + (page.pageNumber() + 1);
            source.sendSuccess(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.admin.abandoned.next")
                    .formatted(nextPageCommand))
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, nextPageCommand))), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static MutableComponent describeIsland(final Island island) {
        final IslandGridSlot slot = IslandGridSlot.containing(island.getCenter());
        final String islandId = island.getId().toString();
        final ForgeIsland forgeIsland = (ForgeIsland) island;
        final String entryKey = forgeIsland.purgeEligibility() == PurgeEligibility.NO_TRACKED_CHUNKS
                ? "commands.admin.abandoned.entry.no.chunks"
                : "commands.admin.abandoned.entry";
        return new TextComponent(SkyBlockAddonLanguage.getLocalizedString(entryKey)
                .formatted(islandId, island.getCenter().getX(), island.getCenter().getZ(), slot.column(), slot.row(),
                        forgeIsland.getModifiedChunks().size()))
                .withStyle(ChatFormatting.YELLOW)
                .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, islandId)));
    }
}
