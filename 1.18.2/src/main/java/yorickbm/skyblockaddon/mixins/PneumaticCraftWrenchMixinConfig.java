package yorickbm.skyblockaddon.mixins;

import me.desht.pneumaticcraft.api.block.IPneumaticWrenchable;
import me.desht.pneumaticcraft.common.network.PacketModWrenchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

/**
 * PneumaticCraft turns a click with another mod's wrench into its own packet, which wrenches the block (sneaking
 * dismantles it, else rotates it) or a drone without any Forge event.
 */
@Mixin(value = PacketModWrenchBlock.class, remap = false)
public abstract class PneumaticCraftWrenchMixinConfig {

    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Lme/desht/pneumaticcraft/api/block/IPneumaticWrenchable;onWrenched(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/InteractionHand;)Z"))
    private boolean judgeModdedWrench(final IPneumaticWrenchable wrenched, final Level level, final Player player, final BlockPos position, final Direction side,
                                      final InteractionHand hand) {
        if (MixinPermissionChecks.denyPneumaticCraftWrench(player, level, position, wrenched, hand)) return false;
        return wrenched.onWrenched(level, player, position, side, hand);
    }
}
