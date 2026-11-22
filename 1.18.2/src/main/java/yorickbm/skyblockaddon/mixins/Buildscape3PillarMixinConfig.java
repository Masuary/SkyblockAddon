package yorickbm.skyblockaddon.mixins;

import net.minecraft.core.BlockPos;
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
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

/**
 * Buildscape 3.x pillars (Wold's Vaults). The build compiles against Buildscape 4.x, so the 3.x classes are named.
 */
@SuppressWarnings("unresolvable-target")
@Mixin(targets = {"com.kingodogo.buildscape.block.PillarBlock", "com.kingodogo.buildscape.block.AshenKingPillarBlock"}, remap = false)
public abstract class Buildscape3PillarMixinConfig {

    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true)
    private void onUse(final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand,
                       final BlockHitResult hit, final CallbackInfoReturnable<InteractionResult> callback) {
        MixinPermissionChecks.itemHolderUseOverride(player, level, pos, hand).ifPresent(callback::setReturnValue);
    }
}
