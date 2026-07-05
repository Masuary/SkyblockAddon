package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.registries.PermissionGroupRegistry;
import yorickbm.skyblockaddon.core.util.ResourceManager;

import java.nio.file.Path;
import java.util.function.Predicate;

public class ConfigReloadCommand {
    private static final Logger LOGGER = LogManager.getLogger();

    public ConfigReloadCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Cmds.literal("island")
            .then(Cmds.literal("admin")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Cmds.literal("reload")
                        .executes(context -> execute(context.getSource()))
                )
            )
        );
    }

    public int execute(final CommandSourceStack command) {
        final Predicate<String> isModLoaded = mod -> ModList.get().isLoaded(mod);
        final Path configDir = FMLPaths.CONFIGDIR.get();
        final Path modDir = configDir.resolve(SkyblockAddonCore.MOD_ID);

        // Re-run resource extraction (language, configs, registries)
        ResourceManager.commonSetup(configDir);

        // Reload groups
        final Path groupsDir = modDir.resolve("registries/groups/");
        final PermissionGroupRegistry groupRegistry = PermissionGroupRegistry.getInstance();
        final PermissionGroupRegistry.State oldGroupState = groupRegistry.snapshotState();
        final PermissionManager permissionManager = PermissionManager.getInstance();
        final java.util.List<yorickbm.skyblockaddon.core.permissions.Permission> oldPermissionState =
                permissionManager.snapshotState();

        // Keep deployed legacy overrides and add permissions introduced by the per-mod registries.
        final Path newPermsDir  = modDir.resolve("registries/permissions/");
        final Path oldPermsFile = modDir.resolve("registries/PermissionRegistry.json");

        final int count;
        try {
            groupRegistry.loadFromDirectory(groupsDir, isModLoaded);
            count = permissionManager.loadPermissions(oldPermsFile, newPermsDir, isModLoaded);
            GUILibraryRegistry.registerFolder(SkyblockAddonCore.MOD_ID, modDir.resolve("guis/"));
        } catch (final RuntimeException exception) {
            groupRegistry.restoreState(oldGroupState);
            permissionManager.restoreState(oldPermissionState);
            LOGGER.error("Configuration reload failed; previous groups and permissions remain active", exception);
            command.sendFailure(new TextComponent("Reload failed; the previous configuration remains active: "
                    + exception.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }

        LOGGER.info("Reloaded {} permissions, {} GUIs.", count, GUILibraryRegistry.getGuis());
        command.sendSuccess(new TextComponent(
                "Reloaded " + count + " permissions and " + GUILibraryRegistry.getGuis() + " GUIs."
        ).withStyle(ChatFormatting.GREEN), true);

        return Command.SINGLE_SUCCESS;
    }
}
