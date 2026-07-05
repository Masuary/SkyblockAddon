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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import nl.requios.effortlessbuilding.helper.SurvivalHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.InteractionHandler;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = SurvivalHelper.class, remap = false)
public abstract class EffortlessBuildingMixinConfig {
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onPlaceBlock(
            final Level world,
            final Player player,
            final BlockPos position,
            final BlockState blockState,
            final ItemStack originalStack,
            final Direction facing,
            final Vec3 hitLocation,
            final boolean skipPlaceCheck,
            final boolean skipCollisionCheck,
            final boolean playSound,
            final CallbackInfoReturnable<Boolean> callback
    ) {
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) return;
        if (!(world instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) return;
        if (serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return;

        final Island targetIsland = IslandManager.getInstance()
                .getIslandByPos(ForgeConverter.ForgeToInternalVec3i(position));
        if (targetIsland == null || targetIsland.isOwner(serverPlayer.getUUID())) return;

        final AtomicReference<Island> island = new AtomicReference<>(targetIsland);
        if (!InteractionHandler.checkPlayerInteraction(
                island,
                serverPlayer,
                serverLevel,
                position,
                originalStack,
                "onPlaceBlock"
        )) return;

        denyAndResynchronize(serverPlayer, serverLevel, position);
        callback.setReturnValue(false);
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onBreakBlock(
            final Level world,
            final Player player,
            final BlockPos position,
            final boolean skipChecks,
            final CallbackInfoReturnable<Boolean> callback
    ) {
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) return;
        if (!(world instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) return;
        if (serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return;

        final Island targetIsland = IslandManager.getInstance()
                .getIslandByPos(ForgeConverter.ForgeToInternalVec3i(position));
        if (targetIsland == null || targetIsland.isOwner(serverPlayer.getUUID())) return;

        final AtomicReference<Island> island = new AtomicReference<>(targetIsland);
        if (!InteractionHandler.checkPlayerInteraction(
                island,
                serverPlayer,
                serverLevel,
                position,
                serverPlayer.getMainHandItem(),
                "onLeftClickBlock"
        )) return;

        denyAndResynchronize(serverPlayer, serverLevel, position);
        callback.setReturnValue(false);
    }

    private static void denyAndResynchronize(
            final ServerPlayer player,
            final ServerLevel level,
            final BlockPos position
    ) {
        player.displayClientMessage(
                new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                        .withStyle(ChatFormatting.DARK_RED),
                true
        );
        ServerHelper.SendPacket(player, new ClientboundBlockUpdatePacket(position, level.getBlockState(position)));
    }
}
