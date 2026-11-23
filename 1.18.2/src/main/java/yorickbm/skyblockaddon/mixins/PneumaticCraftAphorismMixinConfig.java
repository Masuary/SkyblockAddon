package yorickbm.skyblockaddon.mixins;

import me.desht.pneumaticcraft.common.network.LocationIntPacket;
import me.desht.pneumaticcraft.common.network.PacketAphorismTileUpdate;
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
 * PneumaticCraft's aphorism tile edit packet, judged on the server thread before the tile is rewritten.
 */
@Mixin(value = PacketAphorismTileUpdate.class, remap = false)
public abstract class PneumaticCraftAphorismMixinConfig extends LocationIntPacket {

    protected PneumaticCraftAphorismMixinConfig(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    @Inject(method = "lambda$handle$2", at = @At("HEAD"), cancellable = true)
    private void judgeTileEdit(final Supplier<NetworkEvent.Context> context, final CallbackInfo callback) {
        final ServerPlayer sender = context.get().getSender();
        if (sender != null && MixinPermissionChecks.denyPneumaticCraftTileEdit(sender, this.pos)) callback.cancel();
    }
}
