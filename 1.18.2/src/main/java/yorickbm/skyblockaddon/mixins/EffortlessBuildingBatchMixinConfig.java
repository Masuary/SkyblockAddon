package yorickbm.skyblockaddon.mixins;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import nl.requios.effortlessbuilding.buildmodifier.BuildModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.InteractionHandler;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = BuildModifiers.class, remap = false)
public abstract class EffortlessBuildingBatchMixinConfig {
    @Inject(method = "onBlockPlaced", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onBatchPlaceBlocks(
            final Player player,
            final List<BlockPos> positions,
            final Direction sideHit,
            final Vec3 hitLocation,
            final boolean placeStartPosition,
            final CallbackInfo callback
    ) {
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) return;
        if (!(player.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD || serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return;

        for (final BlockPos position : positions) {
            if (!isBlocked(serverPlayer, serverLevel, position, "onPlaceBlock")) continue;
            denyAndResynchronize(serverPlayer, serverLevel, positions);
            callback.cancel();
            return;
        }
    }

    @Inject(method = "onBlockBroken", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onBatchBreakBlocks(
            final Player player,
            final List<BlockPos> positions,
            final boolean breakStartPosition,
            final CallbackInfo callback
    ) {
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) return;
        if (!(player.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD || serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return;

        for (final BlockPos position : positions) {
            if (!isBlocked(serverPlayer, serverLevel, position, "onLeftClickBlock")) continue;
            denyAndResynchronize(serverPlayer, serverLevel, positions);
            callback.cancel();
            return;
        }
    }

    private static boolean isBlocked(
            final ServerPlayer player,
            final ServerLevel level,
            final BlockPos position,
            final String trigger
    ) {
        final Island targetIsland = IslandManager.getInstance()
                .getIslandByPos(ForgeConverter.ForgeToInternalVec3i(position));
        if (targetIsland == null || targetIsland.isOwner(player.getUUID())) return false;

        return InteractionHandler.checkPlayerInteraction(
                new AtomicReference<>(targetIsland),
                player,
                level,
                position,
                player.getMainHandItem(),
                trigger
        );
    }

    private static void denyAndResynchronize(
            final ServerPlayer player,
            final ServerLevel level,
            final List<BlockPos> positions
    ) {
        player.displayClientMessage(
                new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                        .withStyle(ChatFormatting.DARK_RED),
                true
        );
        for (final BlockPos position : positions) {
            ServerHelper.SendPacket(player, new ClientboundBlockUpdatePacket(position, level.getBlockState(position)));
        }
    }
}
