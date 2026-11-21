package yorickbm.skyblockaddon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class BuildingBlock {
    private final BlockState state;
    private final BlockPos pos;

    public BuildingBlock(final BlockPos blockPos, final BlockState blockState) {
        state = blockState;
        pos = blockPos;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }
}