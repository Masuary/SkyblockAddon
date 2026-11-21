package yorickbm.skyblockaddon.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.List;
import java.util.Objects;

/**
 * Sends overworld players standing on an island that banned them to spawn, checked every two seconds.
 */
public class IslandBanEvents {
    private static final int CHECK_INTERVAL_TICKS = 40;

    private int ticksUntilNextCheck = CHECK_INTERVAL_TICKS;

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (--ticksUntilNextCheck > 0) return;
        ticksUntilNextCheck = CHECK_INTERVAL_TICKS;

        if (IslandManager.getInstance().getIslands().stream().allMatch(island -> island.getBannedPlayers().isEmpty())) return;

        final MinecraftServer server = Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "Server tick without a running server");
        final ServerLevel overworld = server.overworld();
        for (final ServerPlayer player : List.copyOf(overworld.players())) {
            if (player instanceof FakePlayer) continue;
            final Island island = IslandManager.getInstance().getIslandByPos(ForgeConverter.forgeToInternalVec3i(player.getOnPos()));
            if (island == null || !IslandActionService.isBannedFrom(island, player)) continue;
            if (island.getIslandBoundingBox().isInside(ForgeConverter.forgeToInternalVec3i(overworld.getSharedSpawnPos()))) continue;

            ForgeIsland.sendToWorldSpawn(player);
            player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.banned.removed")
                    .formatted(PlayerNames.nameOf(server, island.getOwner()))).withStyle(ChatFormatting.RED), player.getUUID());
        }
    }
}
