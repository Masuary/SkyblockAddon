package yorickbm.skyblockaddon.mixins;

import me.desht.pneumaticcraft.common.hacking.WorldAndCoord;
import me.desht.pneumaticcraft.common.pneumatic_armor.handlers.HackHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

/**
 * A Pneumatic Helmet hack completes in the level the player is in when the timer runs out, which can differ from where
 * it started (the hack survives a dimension change), so the running hack is judged every server tick in that level and
 * dropped once it is denied.
 */
@Mixin(value = HackHandler.HackData.class, remap = false)
public abstract class PneumaticCraftHackCompletionMixinConfig {

    @Shadow
    private WorldAndCoord hackedBlockPos;

    @Shadow
    private Entity hackedEntity;

    @Shadow
    public abstract void setHackedBlockPos(WorldAndCoord position);

    @Shadow
    public abstract void setHackedEntity(Entity entity);

    @Inject(method = "tickBlockHack", at = @At("HEAD"), cancellable = true)
    private void judgeRunningBlockHack(final ServerPlayer player, final CallbackInfo callback) {
        if (this.hackedBlockPos != null && MixinPermissionChecks.denyPneumaticCraftBlockHack(player, this.hackedBlockPos.pos)) {
            setHackedBlockPos(null);
            callback.cancel();
        }
    }

    @Inject(method = "tickEntityHack", at = @At("HEAD"), cancellable = true)
    private void judgeRunningEntityHack(final ServerPlayer player, final CallbackInfo callback) {
        if (this.hackedEntity != null && MixinPermissionChecks.denyPneumaticCraftEntityHack(player, this.hackedEntity)) {
            setHackedEntity(null);
            callback.cancel();
        }
    }
}
