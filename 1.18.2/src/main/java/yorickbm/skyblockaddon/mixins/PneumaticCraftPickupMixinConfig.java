package yorickbm.skyblockaddon.mixins;

import me.desht.pneumaticcraft.api.drone.IDrone;
import me.desht.pneumaticcraft.common.ai.DroneEntityAIPickupItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.events.PneumaticCraftProtectionEvents;

import java.util.UUID;

@Mixin(value = DroneEntityAIPickupItems.class, remap = false)
public abstract class PneumaticCraftPickupMixinConfig {
    @Inject(method = "tryPickupItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skyblockaddon$protectDronePickup(
            final IDrone drone,
            final ItemEntity itemEntity,
            final CallbackInfo callback
    ) {
        final UUID ownerId = drone.getOwnerUUID();
        if (drone instanceof Entity droneEntity && ownerId != null && ownerId.equals(droneEntity.getUUID())) {
            // Amadron delivery drones intentionally have no player owner.
            return;
        }
        if (PneumaticCraftProtectionEvents.isActionDenied(
                ownerId,
                itemEntity.blockPosition(),
                itemEntity.getLevel()
        )) {
            callback.cancel();
        }
    }
}
