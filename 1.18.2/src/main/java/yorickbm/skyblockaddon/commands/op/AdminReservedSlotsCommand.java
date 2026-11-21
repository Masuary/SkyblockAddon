package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.IslandSlot;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Reserves island grid slots for this world so /island create never builds there. The world
 * keeps these next to the grid position; the config list island.reserved_slots adds more.
 */
public class AdminReservedSlotsCommand {
    private static final Comparator<IslandSlot> SLOT_ORDER = Comparator.comparingInt(IslandSlot::x).thenComparingInt(IslandSlot::z);

    public AdminReservedSlotsCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.adminLiteral()
                        .then(Cmds.literal("reserve")
                                .requires(Cmds.permissionLevel(Commands.LEVEL_GAMEMASTERS))
                                .then(Cmds.argument("x", IntegerArgumentType.integer())
                                        .then(Cmds.argument("z", IntegerArgumentType.integer())
                                                .executes(context -> reserve(context.getSource(),
                                                        IslandSlot.nearestTo(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z")))))))
                        .then(Cmds.literal("unreserve")
                                .requires(Cmds.permissionLevel(Commands.LEVEL_GAMEMASTERS))
                                .then(Cmds.argument("x", IntegerArgumentType.integer())
                                        .then(Cmds.argument("z", IntegerArgumentType.integer())
                                                .executes(context -> unreserve(context.getSource(),
                                                        IslandSlot.nearestTo(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z")))))))
                        .then(Cmds.literal("reserved")
                                .requires(Cmds.permissionLevel(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> listReservedSlots(context.getSource())))
                )
        );
    }

    private int reserve(final CommandSourceStack source, final IslandSlot slot) {
        if (IslandManager.getInstance().reserveSlot(slot)) {
            source.sendSuccess(localized("commands.admin.reserve.added", slot.format()).withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(localized("commands.admin.reserve.exists", slot.format()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int unreserve(final CommandSourceStack source, final IslandSlot slot) {
        if (IslandManager.getInstance().unreserveSlot(slot)) {
            source.sendSuccess(localized("commands.admin.reserve.removed", slot.format()).withStyle(ChatFormatting.GREEN), true);
        } else if (IslandManager.getInstance().getConfiguredReservedSlots().contains(slot)) {
            source.sendFailure(localized("commands.admin.reserve.configured", slot.format()));
        } else {
            source.sendFailure(localized("commands.admin.reserve.missing", slot.format()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int listReservedSlots(final CommandSourceStack source) {
        final List<IslandSlot> configuredSlots = sorted(IslandManager.getInstance().getConfiguredReservedSlots());
        final List<IslandSlot> adminSlots = sorted(IslandManager.getInstance().getAdminReservedSlots());
        if (configuredSlots.isEmpty() && adminSlots.isEmpty()) {
            source.sendSuccess(localized("commands.admin.reserved.none"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(localized("commands.admin.reserved.header", configuredSlots.size(), adminSlots.size()).withStyle(ChatFormatting.GREEN), false);
        configuredSlots.forEach(slot -> source.sendSuccess(localized("commands.admin.reserved.entry.configured", slot.format()), false));
        adminSlots.forEach(slot -> source.sendSuccess(localized("commands.admin.reserved.entry.admin", slot.format()), false));
        return Command.SINGLE_SUCCESS;
    }

    private static List<IslandSlot> sorted(final Collection<IslandSlot> slots) {
        return slots.stream().sorted(SLOT_ORDER).toList();
    }

    private static TextComponent localized(final String languageKey, final Object... arguments) {
        return new TextComponent(SkyBlockAddonLanguage.getLocalizedString(languageKey).formatted(arguments));
    }
}
