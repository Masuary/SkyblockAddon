package yorickbm.skyblockaddon.mixins;

import me.desht.pneumaticcraft.common.network.LocationIntPacket;
import me.desht.pneumaticcraft.common.network.PacketSyncRedstoneModuleToServer;
import me.desht.pneumaticcraft.common.network.PacketTubeModuleColor;
import me.desht.pneumaticcraft.common.network.PacketUpdateTubeModule;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
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
 * PneumaticCraft's tube module screens open on the client and send their settings (thresholds, colours, redstone
 * logic), judged on the server thread like a right click on the tube. A packet for a tube out of reach is dropped
 * before anything is looked up: the colour packet has no reach check of its own.
 */
@Mixin(value = {PacketUpdateTubeModule.class, PacketTubeModuleColor.class, PacketSyncRedstoneModuleToServer.class}, remap = false)
public abstract class PneumaticCraftTubeModuleMixinConfig extends LocationIntPacket {

    protected PneumaticCraftTubeModuleMixinConfig(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    @Inject(method = "lambda$handle$1", at = @At("HEAD"), cancellable = true)
    private void judgeTubeModuleEdit(final Supplier<NetworkEvent.Context> context, final CallbackInfo callback) {
        final ServerPlayer sender = context.get().getSender();
        if (sender == null) return;
        if (!PneumaticCraftUtils.canPlayerReach(sender, this.pos) || MixinPermissionChecks.denyPneumaticCraftBlockEdit(sender, this.pos)) callback.cancel();
    }
}
