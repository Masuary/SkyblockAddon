package yorickbm.skyblockaddon.commands.interfaces;

import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;

public abstract class OverWorldCommandStack implements CommandStack {
    public int execute(final CommandSourceStack command, final ServerPlayer executor) {
        return isInOverworld(executor) ? Command.SINGLE_SUCCESS : 0;
    }

    public boolean isInOverworld(final ServerPlayer target) {
        if (target.level.dimension() != Level.OVERWORLD) {
            target.sendMessage(
                    new TextComponent(
                            SkyBlockAddonLanguage.getLocalizedString("commands.not.in.overworld")
                    ).withStyle(ChatFormatting.RED),
                    target.getUUID());
            return false;
        }
        return true;
    }
}
