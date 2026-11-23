package yorickbm.skyblockaddon.mixins;

import me.desht.pneumaticcraft.common.network.PacketHackingEntityStart;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

import java.util.function.Supplier;

/**
 * The Pneumatic Helmet's entity hack (taming hack included) starts with this packet and completes on the server
 * without any Forge event, so a denied hack is never started.
 */
@Mixin(value = PacketHackingEntityStart.class, remap = false)
public abstract class PneumaticCraftHackingEntityMixinConfig {

    @Shadow
    @Final
    private int entityId;

    @Inject(method = "lambda$handle$1", at = @At("HEAD"), cancellable = true)
    private void judgeEntityHack(final Supplier<NetworkEvent.Context> context, final CallbackInfo callback) {
        final ServerPlayer sender = context.get().getSender();
        if (sender == null) return;
        final Entity target = sender.getLevel().getEntity(this.entityId);
        if (target != null && MixinPermissionChecks.denyPneumaticCraftEntityHack(sender, target)) callback.cancel();
    }
}
