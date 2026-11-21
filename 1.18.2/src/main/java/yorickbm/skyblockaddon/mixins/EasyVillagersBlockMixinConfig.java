package yorickbm.skyblockaddon.mixins;

import de.maxhenkel.easyvillagers.blocks.VillagerBlockBase;
import de.maxhenkel.easyvillagers.events.BlockEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = BlockEvents.class, remap = false)
public abstract class EasyVillagersBlockMixinConfig {

    @Inject(method = "onBlockClick", at = @At("HEAD"), cancellable = true)
    public void denyVillagerBlockClick(final PlayerInteractEvent.RightClickBlock event, final CallbackInfo callback) {
        if (!(event.getPlayer() instanceof final ServerPlayer player) || !(event.getWorld() instanceof final ServerLevel level)) return;
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof VillagerBlockBase)) return;

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return;

        if (InteractionHandler.checkPlayerInteraction(standingOn, player, level, event.getPos(), event.getItemStack(), PermissionTriggers.ON_EASY_VILLAGERS_BLOCK_INTERACT)) {
            InteractionHandler.denyBlockClick(event, player);
            callback.cancel();
        }
    }
}
