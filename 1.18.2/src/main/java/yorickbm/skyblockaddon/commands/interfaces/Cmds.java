package yorickbm.skyblockaddon.commands.interfaces;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

public class Cmds {
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    /**
     * Brigadier merges every registration of "island admin" into one node and keeps the requirement of the
     * first one, so every admin command must build that node here and gate itself on its own subcommand literal.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> adminLiteral() {
        return literal("admin").requires(source -> source.hasPermission(Commands.LEVEL_MODERATORS));
    }

    public static Predicate<CommandSourceStack> permissionLevel(final int level) {
        return source -> source.hasPermission(level);
    }

    public static Predicate<CommandSourceStack> playerWithPermissionLevel(final int level) {
        return source -> source.getEntity() instanceof ServerPlayer && source.hasPermission(level);
    }
}
