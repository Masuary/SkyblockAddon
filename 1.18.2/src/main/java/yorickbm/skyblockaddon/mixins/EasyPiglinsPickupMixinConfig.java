package yorickbm.skyblockaddon.mixins;

import de.maxhenkel.easypiglins.events.PiglinEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

@Mixin(value = PiglinEvents.class, remap = false)
public abstract class EasyPiglinsPickupMixinConfig {

    @Inject(method = "onClick", at = @At("HEAD"), cancellable = true)
    public void denyPiglinPickup(final PlayerInteractEvent.EntityInteract event, final CallbackInfo callback) {
        if (!(event.getTarget() instanceof final Piglin piglin) || piglin.isBaby()) return;
        if (!(event.getPlayer() instanceof final ServerPlayer player) || !player.isShiftKeyDown()) return;

        if (MixinPermissionChecks.denyMobPickup(player, piglin.getType(), PermissionTriggers.ON_PICKUP_PIGLIN)) {
            callback.cancel();
        }
    }
}
