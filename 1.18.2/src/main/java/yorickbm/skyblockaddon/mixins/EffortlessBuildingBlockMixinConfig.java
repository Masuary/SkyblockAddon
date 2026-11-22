package yorickbm.skyblockaddon.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
     * An air state or an empty stack makes placeBlock remove the block instead, which is judged as a break. Refused for
     * everyone: placing the state that is already there (placeBlock drops the existing block's loot before its setBlock
     * fails on the unchanged state, a free drop per redo) and a two-block placement whose second half would overwrite a
     * block.
     */
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private static void denyPlaceOnForeignIsland(final Level level, final Player player, final BlockPos position, final BlockState blockState,
                                                 final ItemStack placingStack, final Direction facing, final Vec3 hitVector,
                                                 final boolean skipPlaceCheck, final boolean skipCollisionCheck, final boolean playSound,
                                                 final CallbackInfoReturnable<Boolean> callback) {
        final boolean removesBlock = blockState.isAir() || placingStack.isEmpty();
        if (!removesBlock && (level.getBlockState(position) == blockState || overwritesBlockWithSecondHalf(level, position, blockState))) {
            callback.setReturnValue(false);
            return;
        }

        final boolean denied = removesBlock
                ? MixinPermissionChecks.denyRangedBreak(player, level, position)
                : MixinPermissionChecks.denyRangedPlacement(player, level, position, placingStack, blockState);
        if (denied) callback.setReturnValue(false);
    }

    /**
     * Doors, tall plants and beds write their second half in setPlacedBy without any check or drop. Vanilla only places
     * them when that position is free, but Effortless Building's modifier copies skip that test, so a bed foot next to a
     * chest would delete the chest.
     */
    private static boolean overwritesBlockWithSecondHalf(final Level level, final BlockPos position, final BlockState placedState) {
        final BlockPos secondHalfPosition;
        if (placedState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && placedState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            secondHalfPosition = position.above();
        } else if (placedState.getBlock() instanceof BedBlock && placedState.getValue(BedBlock.PART) == BedPart.FOOT) {
            secondHalfPosition = position.relative(placedState.getValue(BedBlock.FACING));
        } else {
            return false;
        }
        final BlockState secondHalfState = level.getBlockState(secondHalfPosition);
        return !secondHalfState.isAir() && !secondHalfState.getMaterial().isReplaceable();
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private static void denyBreakOnForeignIsland(final Level level, final Player player, final BlockPos position, final boolean skipChecks,
                                                 final CallbackInfoReturnable<Boolean> callback) {
        if (MixinPermissionChecks.denyRangedBreak(player, level, position)) callback.setReturnValue(false);
    }
}
