package yorickbm.skyblockaddon.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.buildmodifier.BuildModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

import java.util.List;

/**
 * A build mode action is refused as a whole when any of its start positions is denied, so a line or wall is never
 * built halfway; the per-block check still judges the positions the modifiers add.
 */
@Mixin(value = BuildModifiers.class, remap = false)
public abstract class EffortlessBuildingBatchMixinConfig {

    @Inject(method = "onBlockPlaced", at = @At("HEAD"), cancellable = true)
    private static void denyBatchPlaceOnForeignIsland(final Player player, final List<BlockPos> startPositions, final Direction sideHit,
                                                      final Vec3 hitVector, final boolean placeStartPosition, final CallbackInfo callback) {
        if (MixinPermissionChecks.denyRangedPlacements(player, startPositions)) callback.cancel();
    }

    @Inject(method = "onBlockBroken", at = @At("HEAD"), cancellable = true)
    private static void denyBatchBreakOnForeignIsland(final Player player, final List<BlockPos> startPositions, final boolean breakStartPosition,
                                                      final CallbackInfo callback) {
        if (MixinPermissionChecks.denyRangedBreaks(player, startPositions)) callback.cancel();
    }
}
