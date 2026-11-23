package yorickbm.skyblockaddon.mixins;

import me.desht.pneumaticcraft.common.network.LocationIntPacket;
import me.desht.pneumaticcraft.common.network.PacketHackingBlockStart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

import java.util.function.Supplier;

/**
 * The Pneumatic Helmet's block hack starts with this packet and completes on the server without any Forge event, so a
 * denied hack is never started.
 */
@Mixin(value = PacketHackingBlockStart.class, remap = false)
public abstract class PneumaticCraftHackingBlockMixinConfig extends LocationIntPacket {

    protected PneumaticCraftHackingBlockMixinConfig(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true)
    private void judgeBlockHack(final Supplier<NetworkEvent.Context> context, final CallbackInfo callback) {
        final ServerPlayer sender = context.get().getSender();
        if (sender != null && MixinPermissionChecks.denyPneumaticCraftBlockHack(sender, this.pos)) callback.cancel();
    }
}
