package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

public class AdminPermissionCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String TARGET_MEMBERS = "members";
    private static final String TARGET_VISITORS = "visitors";
    private static final String TARGET_ALL = "all";

    public AdminPermissionCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is");
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        final var permissionIdArgument = Cmds.argument("permissionId", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        PermissionManager.getInstance().getPermissions().stream().map(Permission::getId),
                        builder))
                .then(targetArgument(TARGET_MEMBERS))
                .then(targetArgument(TARGET_VISITORS))
                .then(targetArgument(TARGET_ALL));

        dispatcher.register(Cmds.literal(rootLiteral)
                .then(Cmds.literal("admin")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Cmds.literal("permission")
                                .then(Cmds.literal("set")
                                        .then(permissionIdArgument)))));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> targetArgument(final String target) {
        return Cmds.literal(target)
                .then(Cmds.argument("value", BoolArgumentType.bool())
                        .executes(context -> executeBulkPermissionUpdate(
                                context.getSource(),
                                StringArgumentType.getString(context, "permissionId"),
                                target,
                                BoolArgumentType.getBool(context, "value"),
                                false))
                        .then(Cmds.literal("confirm")
                                .executes(context -> executeBulkPermissionUpdate(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "permissionId"),
                                        target,
                                        BoolArgumentType.getBool(context, "value"),
                                        true))));
    }

    private int executeBulkPermissionUpdate(
            final CommandSourceStack command,
            final String requestedPermissionId,
            final String targetGroup,
            final boolean value,
            final boolean confirmed
    ) {
        final String permissionId = PermissionManager.getInstance().getPermissions().stream()
                .map(Permission::getId)
                .filter(id -> id.equalsIgnoreCase(requestedPermissionId))
                .findFirst()
                .orElse(null);
        if (permissionId == null) {
            command.sendFailure(new TextComponent("Unknown permission ID: " + requestedPermissionId));
            return 0;
        }

        final Collection<Island> islands = IslandManager.getInstance().getIslands();
        if (islands.isEmpty()) {
            command.sendFailure(new TextComponent("No islands exist on this server."));
            return 0;
        }

        final List<PermissionChange> changes = collectChanges(islands, permissionId, targetGroup, value);
        final long islandsUpdated = changes.stream().map(PermissionChange::island).distinct().count();
        final int groupsUpdated = changes.size();

        if (!confirmed) {
            command.sendSuccess(new TextComponent("Preview: setting permission '" + permissionId + "' to " + value
                    + " for '" + targetGroup + "' would update " + islandsUpdated + " islands and "
                    + groupsUpdated + " groups. Append 'confirm' to apply.").withStyle(ChatFormatting.YELLOW), false);
            return Command.SINGLE_SUCCESS;
        }

        if (changes.isEmpty()) {
            command.sendSuccess(new TextComponent("No changes are required for permission '" + permissionId + "'.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return Command.SINGLE_SUCCESS;
        }

        changes.forEach(change -> change.group().setPermission(permissionId, value));
        try {
            persistAllIslands(command);
        } catch (final RuntimeException exception) {
            changes.forEach(change -> change.group().setPermission(permissionId, change.previousValue()));
            LOGGER.error("Failed to persist islands after bulk permission update; in-memory changes were rolled back", exception);
            command.sendFailure(new TextComponent("Permission changes were rolled back because persistence failed: "
                    + exception.getMessage()));
            return 0;
        }

        command.sendSuccess(new TextComponent("Set permission '" + permissionId + "' to " + value
                + " for '" + targetGroup + "' on " + islandsUpdated + " islands and "
                + groupsUpdated + " groups.").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private List<PermissionChange> collectChanges(
            final Collection<Island> islands,
            final String permissionId,
            final String targetGroup,
            final boolean value
    ) {
        final List<PermissionChange> changes = new ArrayList<>();
        for (final Island island : islands) {
            for (final IslandGroup group : getTargetGroups(island, targetGroup)) {
                final boolean previousValue = group.canDo(permissionId);
                if (previousValue != value) {
                    changes.add(new PermissionChange(island, group, previousValue));
                }
            }
        }
        return changes;
    }

    private List<IslandGroup> getTargetGroups(final Island island, final String targetGroup) {
        if (TARGET_MEMBERS.equals(targetGroup)) {
            return island.getMembersGroup() == null ? List.of() : List.of(island.getMembersGroup());
        }
        if (TARGET_VISITORS.equals(targetGroup)) {
            return island.getDefaultGroup() == null ? List.of() : List.of(island.getDefaultGroup());
        }
        return new ArrayList<>(island.getGroups());
    }

    private void persistAllIslands(final CommandSourceStack command) {
        final var overworld = command.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("The overworld is not loaded");
        }

        final SkyblockAddonWorldCapability capability = overworld
                .getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY)
                .resolve()
                .orElseThrow(() -> new IllegalStateException("SkyblockAddon world capability is not attached"));
        capability.saveIslandsToDisk();
    }

    private record PermissionChange(Island island, IslandGroup group, boolean previousValue) { }
}
