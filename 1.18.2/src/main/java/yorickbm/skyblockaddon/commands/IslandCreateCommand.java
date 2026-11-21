package yorickbm.skyblockaddon.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.commands.interfaces.Cmds;
import yorickbm.skyblockaddon.commands.interfaces.OverWorldCommandStack;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.IslandCreationQueue;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.WeakHashMap;

public class IslandCreateCommand extends OverWorldCommandStack {
    private static final Map<UUID, Long> cooldowns = Collections.synchronizedMap(new WeakHashMap<>());

    public IslandCreateCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "island");
        register(dispatcher, "is"); // alias
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, String rootLiteral) {
        dispatcher.register(
                Cmds.literal(rootLiteral)
                        .then(Cmds.literal("create")
                                .requires(source -> source.getEntity() instanceof ServerPlayer)
                                .executes(context -> execute(
                                        context.getSource(),
                                        (ServerPlayer) context.getSource().getEntity()))
                        )
        );
    }

    @Override
    public int execute(final CommandSourceStack command, final ServerPlayer executor) {
        if (!SkyblockAddonConfig.isIslandCreateEnabled()) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.disabled")));
            return Command.SINGLE_SUCCESS;
        }
        if(super.execute(command, executor) == 0) return Command.SINGLE_SUCCESS;

        final UUID executorId = executor.getUUID();
        final IslandCreationQueue creationQueue = IslandCreationQueue.getInstance();
        final OptionalInt queuedPosition = creationQueue.getPosition(executorId);
        if (queuedPosition.isPresent()) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.already.queued").formatted(queuedPosition.getAsInt())));
            return Command.SINGLE_SUCCESS;
        }
        if(this.hasActiveCooldown(executor)) {
            command.sendFailure(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("commands.create.cooldown"), (this.getCooldownSecondsLeft(executor) + "s"))));
            return Command.SINGLE_SUCCESS;
        }
        if (IslandManager.getInstance().getIslandByEntityUUID(executorId) != null) {
            command.sendFailure(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.create.already")));
            return Command.SINGLE_SUCCESS;
        }

        cooldowns.put(executorId, System.currentTimeMillis());
        final int position = creationQueue.enqueue(executorId, executor.getGameProfile().getName(), () -> cooldowns.remove(executorId));
        final String queuedMessage = position == 1
                ? SkyBlockAddonLanguage.getLocalizedString("commands.create.generating")
                : SkyBlockAddonLanguage.getLocalizedString("commands.create.queued").formatted(position);
        executor.sendMessage(new TextComponent(queuedMessage).withStyle(ChatFormatting.GREEN), executorId);

        return Command.SINGLE_SUCCESS;
    }

    private boolean hasActiveCooldown(final ServerPlayer player) {
        final UUID uuid = player.getUUID();
        final long currentTime = System.currentTimeMillis();

        final String cooldownStr = SkyblockAddonConfig.getForKey("island.create.cooldown");
        long cooldownSeconds;
        try {
            cooldownSeconds = Long.parseLong(cooldownStr);
        } catch (NumberFormatException e) {
            cooldownSeconds = SkyblockAddonCore.DEFAULT_CREATE_COOLDOWN; //Default value
        }

        final long cooldownTimeMs = cooldownSeconds * 1000;

        final Long lastUsed = cooldowns.get(uuid);
        if (lastUsed != null && (currentTime - lastUsed) < cooldownTimeMs) {
            return true;
        }
        return false;
    }

    private long getCooldownSecondsLeft(final ServerPlayer player) {
        final UUID uuid = player.getUUID();

        final Long lastUsed = cooldowns.get(uuid);
        if (lastUsed == null) {
            return 0;
        }

        final long currentTime = System.currentTimeMillis();

        final String cooldownStr = SkyblockAddonConfig.getForKey("island.create.cooldown");
        long cooldownSeconds;
        try {
            cooldownSeconds = Long.parseLong(cooldownStr);
        } catch (final NumberFormatException e) {
            cooldownSeconds = SkyblockAddonCore.DEFAULT_CREATE_COOLDOWN;
        }

        final long cooldownTimeMs = cooldownSeconds * 1000;
        final long timeLeftMs = (lastUsed + cooldownTimeMs) - currentTime;

        // Round up
        return Math.max(0, (timeLeftMs + 999) / 1000);
    }
}
