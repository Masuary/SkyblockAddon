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
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.util.NBTEncoder;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

public class AdminPermissionCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String TARGET_MEMBERS = "members";
    private static final String TARGET_VISITORS = "visitors";
    private static final String TARGET_ALL = "all";

    public AdminPermissionCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Cmds.literal("island")
                .then(Cmds.literal("admin")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Cmds.literal("permission")
                                .then(Cmds.literal("set")
                                        .then(Cmds.argument("permissionId", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        PermissionManager.getInstance().getPermissions().stream().map(Permission::getId),
                                                        builder))
                                                .then(Cmds.literal(TARGET_MEMBERS)
                                                        .then(Cmds.argument("value", BoolArgumentType.bool())
                                                                .executes(ctx -> executeBulkPermissionUpdate(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "permissionId"),
                                                                        TARGET_MEMBERS,
                                                                        BoolArgumentType.getBool(ctx, "value")))))
                                                .then(Cmds.literal(TARGET_VISITORS)
                                                        .then(Cmds.argument("value", BoolArgumentType.bool())
                                                                .executes(ctx -> executeBulkPermissionUpdate(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "permissionId"),
                                                                        TARGET_VISITORS,
                                                                        BoolArgumentType.getBool(ctx, "value")))))
                                                .then(Cmds.literal(TARGET_ALL)
                                                        .then(Cmds.argument("value", BoolArgumentType.bool())
                                                                .executes(ctx -> executeBulkPermissionUpdate(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "permissionId"),
                                                                        TARGET_ALL,
                                                                        BoolArgumentType.getBool(ctx, "value")))))
                                        )
                                )
                        )
                )
        );
    }

    private int executeBulkPermissionUpdate(final CommandSourceStack command, final String permissionId, final String targetGroup, final boolean value) {
        final String canonicalPermissionId = PermissionManager.getInstance().getPermissions().stream()
                .map(Permission::getId)
                .filter(id -> id.equalsIgnoreCase(permissionId))
                .findFirst()
                .orElse(null);
        if (canonicalPermissionId == null) {
            command.sendFailure(new TextComponent("Unknown permission id: " + permissionId));
            return 0;
        }

        final Collection<Island> islands = IslandManager.getInstance().getIslands();
        if (islands.isEmpty()) {
            command.sendFailure(new TextComponent("No islands exist on this server."));
            return 0;
        }

        int islandsTouched = 0;
        int groupsTouched = 0;
        for (final Island island : islands) {
            final int updated = applyPermissionToIsland(island, canonicalPermissionId, targetGroup, value);
            if (updated > 0) {
                islandsTouched++;
                groupsTouched += updated;
            }
        }

        try {
            persistAllIslands(command);
        } catch (final RuntimeException ex) {
            LOGGER.error("Failed to persist islands after bulk permission update", ex);
            command.sendFailure(new TextComponent("Updated " + groupsTouched + " group(s) across " + islandsTouched + " island(s), but failed to write to disk: " + ex.getMessage()));
            return 0;
        }

        command.sendSuccess(new TextComponent("Set permission '" + canonicalPermissionId + "' to " + value
                + " for target '" + targetGroup + "' on " + islandsTouched + " island(s) ("
                + groupsTouched + " group(s) updated).").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private int applyPermissionToIsland(final Island island, final String permissionId, final String targetGroup, final boolean value) {
        int updated = 0;
        switch (targetGroup) {
            case TARGET_MEMBERS -> {
                final IslandGroup group = island.getMembersGroup();
                if (group != null) {
                    group.setPermission(permissionId, value);
                    updated++;
                }
            }
            case TARGET_VISITORS -> {
                final IslandGroup group = island.getDefaultGroup();
                if (group != null) {
                    group.setPermission(permissionId, value);
                    updated++;
                }
            }
            case TARGET_ALL -> {
                for (final IslandGroup group : island.getGroups()) {
                    group.setPermission(permissionId, value);
                    updated++;
                }
            }
        }
        return updated;
    }

    private void persistAllIslands(final CommandSourceStack command) {
        final var overworld = Objects.requireNonNull(command.getServer().getLevel(Level.OVERWORLD));
        final var capability = overworld.getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY);
        if (!capability.isPresent()) {
            throw new IllegalStateException("SkyblockAddon world capability is not attached to the overworld; cannot persist islands.");
        }

        final Path worldPath = command.getServer().getWorldPath(LevelResource.ROOT).normalize();
        final Path filePath = worldPath.resolve("islanddata");

        NBTEncoder.saveToFile(IslandManager.getInstance().getIslands().stream()
                .map(s -> (ForgeIsland) s).toList(), filePath);
    }
}
