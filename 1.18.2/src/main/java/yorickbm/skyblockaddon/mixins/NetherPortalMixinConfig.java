package yorickbm.skyblockaddon.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalMixinConfig {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    public void denyNetherPortalEntry(final BlockState state, final Level level, final BlockPos pos, final Entity entity, final CallbackInfo callback) {
        if (MixinPermissionChecks.denyPortalEntry(entity, level, pos, PermissionTriggers.ON_ENTER_NETHER_PORTAL)) {
            callback.cancel();
        }
    }
}
