package yorickbm.skyblockaddon.mixins;

import iskallia.vault.block.TreasurePedestalBlock;
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

@Mixin(value = {TreasurePedestalBlock.class}, remap = false)
public abstract class VaultTreasurePedestalMixinConfig {

    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true)
    private void onUse(final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand,
                       final BlockHitResult hit, final CallbackInfoReturnable<InteractionResult> callback) {
        MixinPermissionChecks.itemHolderUseOverride(player, level, pos, hand).ifPresent(callback::setReturnValue);
    }
}
