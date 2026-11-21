package yorickbm.skyblockaddon.mixins;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.quark.content.tweaks.module.ReacharoundPlacingModule;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = {ReacharoundPlacingModule.class}, remap= false)
public abstract class QuarkBlockPlaceMixinConfig {

    @Shadow
    private boolean validateReacharoundStack(final ItemStack stack) {
        throw new AssertionError("Shadowed by the mixin");
    }

    /**
     * Quark places by reacharound only when this returns a target: the ray trace hit nothing, a block sits below or
     * beside the aim, and a hand holds a stack Quark accepts (a block item, an item tagged quark:reacharound_able such as
     * the trowel, or a configured whitelist entry). Judging only then leaves every other item use alone.
     */
    @Inject(method = "getPlayerReacharoundTarget", at = @At("RETURN"), cancellable = true, remap = false)
    public void onGetReacharoundTarget(final Player player, final CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() == null) return;

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return;

        final ItemStack placingStack = validateReacharoundStack(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
        if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) player, (ServerLevel) player.getLevel(), player.getOnPos(), PermissionTriggers.ON_PLACE_BLOCK, InteractionHandler.subjectOfUntargetedPlacement(placingStack))) {
            InteractionHandler.sendDeniedMessage(player);
            cir.setReturnValue(null);
        }
    }
}
