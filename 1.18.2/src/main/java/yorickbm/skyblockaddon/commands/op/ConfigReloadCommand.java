package yorickbm.skyblockaddon.commands.op;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.SkyBlockAddon;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.util.RegistrySelector;
import yorickbm.skyblockaddon.core.util.ResourceManager;

import java.util.Map;

public class ConfigReloadCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ConfigReloadCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // Alias
    }

    private void register(final CommandDispatcher<CommandSourceStack> dispatcher, final String rootLiteral) {
        dispatcher.register(Cmds.literal(rootLiteral)
            .then(Cmds.adminLiteral()
                .then(Cmds.literal("reload")
                        .requires(Cmds.permissionLevel(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> execute(context.getSource()))
                )
            )
        );
    }

    public int execute(final CommandSourceStack command) {
        try {
            reloadConfiguration();
        } catch (final IllegalStateException reloadFailure) {
            LOGGER.error("Island configuration reload failed; the previous configuration stays active", reloadFailure);
            command.sendFailure(new TextComponent("Reload failed, previous configuration kept: " + reloadFailure.getMessage()));
            return 0;
        }

        command.sendSuccess(new TextComponent("Configuration files have been reloaded!").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private void reloadConfiguration() {
        ResourceManager.commonSetup(FMLPaths.CONFIGDIR.get(), new RegistrySelector(Map.of(
                "PermissionRegistry", () -> {
                    if (ModList.get().isLoaded("the_vault")) {
                        return "registries/permissions/vaulthunters.json";
                    } else {
                        return "registries/permissions/default.json";
                    }
                }
        )));
        //Register guis
        GUILibraryRegistry.registerFolder(SkyblockAddonCore.MOD_ID, FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID + "/guis/"));

        SkyBlockAddon.loadIslandPermissions();
    }
}
