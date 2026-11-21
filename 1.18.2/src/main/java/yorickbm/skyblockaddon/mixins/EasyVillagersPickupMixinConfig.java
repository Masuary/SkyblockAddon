package yorickbm.skyblockaddon.mixins;

import de.maxhenkel.easyvillagers.net.MessagePickUpVillager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

@Mixin(value = MessagePickUpVillager.class, remap = false)
public abstract class EasyVillagersPickupMixinConfig {

    @Inject(method = "executeServerSide", at = @At("HEAD"), cancellable = true)
    public void denyVillagerPickup(final NetworkEvent.Context context, final CallbackInfo callback) {
        final ServerPlayer player = context.getSender();
        if (player == null) return;

        if (MixinPermissionChecks.denyMobPickup(player, EntityType.VILLAGER, PermissionTriggers.ON_PICKUP_VILLAGER)) {
            callback.cancel();
        }
    }
}
