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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private static final Logger LOGGER = LogManager.getLogger("SkyblockAddon-EB");

    @Inject(method = "onBlockPlaced", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onBatchPlaceBlocks(Player player, List<BlockPos> startCoordinates,
                                           Direction sideHit, Vec3 hitVec, boolean placeStartPos,
                                           CallbackInfo ci) {
        LOGGER.info("EB BuildModifiers.onBlockPlaced mixin fired with {} positions by {}", startCoordinates.size(), player.getName().getString());
        if (player instanceof FakePlayer) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(player.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return;

        for (final BlockPos pos : startCoordinates) {
            final Island targetIsland = IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(pos));
            if (targetIsland == null) continue;
            if (targetIsland.isOwner(serverPlayer.getUUID())) continue;

            final AtomicReference<Island> standingOn = new AtomicReference<>(targetIsland);
            if (InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer, serverLevel, pos,
                    serverPlayer.getMainHandItem(), "onPlaceBlock")) {
                serverPlayer.displayClientMessage(
                        new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                                .withStyle(ChatFormatting.DARK_RED), true);
                for (final BlockPos resyncPos : startCoordinates) {
                    ServerHelper.SendPacket(serverPlayer, new ClientboundBlockUpdatePacket(resyncPos, serverLevel.getBlockState(resyncPos)));
                }
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "onBlockBroken", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onBatchBreakBlocks(Player player, List<BlockPos> startCoordinates, boolean breakStartPos,
                                           CallbackInfo ci) {
        LOGGER.info("EB BuildModifiers.onBlockBroken mixin fired with {} positions by {}", startCoordinates.size(), player.getName().getString());
        if (player instanceof FakePlayer) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(player.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return;

        for (final BlockPos pos : startCoordinates) {
            final Island targetIsland = IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(pos));
            if (targetIsland == null) continue;
            if (targetIsland.isOwner(serverPlayer.getUUID())) continue;

            final AtomicReference<Island> standingOn = new AtomicReference<>(targetIsland);
            if (InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer, serverLevel, pos,
                    serverPlayer.getMainHandItem(), "onLeftClickBlock")) {
                serverPlayer.displayClientMessage(
                        new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                                .withStyle(ChatFormatting.DARK_RED), true);
                for (final BlockPos resyncPos : startCoordinates) {
                    ServerHelper.SendPacket(serverPlayer, new ClientboundBlockUpdatePacket(resyncPos, serverLevel.getBlockState(resyncPos)));
                }
                ci.cancel();
                return;
            }
        }
    }
}
