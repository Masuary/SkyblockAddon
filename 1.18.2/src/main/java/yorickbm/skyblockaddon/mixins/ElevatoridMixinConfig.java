package yorickbm.skyblockaddon.mixins;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.vsngamer.elevatorid.network.TeleportHandler;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

@Mixin(value = TeleportHandler.class, remap = false)
public abstract class ElevatoridMixinConfig {

    @Inject(method = "isBadTeleportPacket", at = @At("HEAD"), cancellable = true)
    private static void denyElevatorTeleport(@Coerce final Object teleportRequest, final ServerPlayer player, final CallbackInfoReturnable<Boolean> callback) {
        if (MixinPermissionChecks.denyElevatorUse(player)) {
            callback.setReturnValue(true);
        }
    }
}
