package yorickbm.skyblockaddon.mixins;

import de.maxhenkel.easypiglins.blocks.BartererBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = BartererBlock.class, remap = false)
public abstract class EasyPiglinsBlockMixinConfig {

    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true)
    public void denyBartererUse(final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand,
                                final BlockHitResult hit, final CallbackInfoReturnable<InteractionResult> callback) {
        if (!(player instanceof final ServerPlayer serverPlayer) || !(level instanceof final ServerLevel serverLevel)) return;

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(serverPlayer, standingOn).asBoolean()) return;

        if (InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer, serverLevel, pos, player.getItemInHand(hand), PermissionTriggers.ON_EASY_PIGLINS_BLOCK_INTERACT)) {
            InteractionHandler.sendDeniedMessage(serverPlayer);
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }
}
