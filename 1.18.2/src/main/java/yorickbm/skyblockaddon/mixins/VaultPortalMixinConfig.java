package yorickbm.skyblockaddon.mixins;

import iskallia.vault.block.VaultPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

@Mixin(value = VaultPortalBlock.class, remap = false)
public abstract class VaultPortalMixinConfig {

    @Inject(method = "m_7892_", at = @At("HEAD"), cancellable = true)
    public void denyVaultPortalEntry(final BlockState state, final Level level, final BlockPos pos, final Entity entity, final CallbackInfo callback) {
        if (MixinPermissionChecks.denyPortalEntry(entity, level, pos, PermissionTriggers.ON_ENTER_VAULT_PORTAL)) {
            callback.cancel();
        }
    }
}
