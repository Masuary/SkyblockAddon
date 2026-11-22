package yorickbm.skyblockaddon.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nl.requios.effortlessbuilding.helper.SurvivalHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

/**
 * Every block Effortless Building writes goes through these two methods: build mode and modifier positions, undo and
 * redo, and the randomizer bag. They set or remove the block directly, so no Forge block event judges them.
 */
@Mixin(value = SurvivalHelper.class, remap = false)
public abstract class EffortlessBuildingBlockMixinConfig {

    /**
     * An air state or an empty stack makes placeBlock remove the block instead, which is judged as a break. Placing the
     * state that is already there is refused for everyone: placeBlock drops the existing block's loot before its
     * setBlock fails on the unchanged state, so a redo onto an identical block would hand out free drops.
     */
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private static void denyPlaceOnForeignIsland(final Level level, final Player player, final BlockPos position, final BlockState blockState,
                                                 final ItemStack placingStack, final Direction facing, final Vec3 hitVector,
                                                 final boolean skipPlaceCheck, final boolean skipCollisionCheck, final boolean playSound,
                                                 final CallbackInfoReturnable<Boolean> callback) {
        final boolean removesBlock = blockState.isAir() || placingStack.isEmpty();
        if (!removesBlock && level.getBlockState(position) == blockState) {
            callback.setReturnValue(false);
            return;
        }

        final boolean denied = removesBlock
                ? MixinPermissionChecks.denyRangedBreak(player, level, position)
                : MixinPermissionChecks.denyRangedPlacement(player, level, position, placingStack, blockState);
        if (denied) callback.setReturnValue(false);
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private static void denyBreakOnForeignIsland(final Level level, final Player player, final BlockPos position, final boolean skipChecks,
                                                 final CallbackInfoReturnable<Boolean> callback) {
        if (MixinPermissionChecks.denyRangedBreak(player, level, position)) callback.setReturnValue(false);
    }
}
