package yorickbm.skyblockaddon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class BuildingBlock {
    private final BlockState state;
    private final BlockPos pos;

    public BuildingBlock(final BlockPos blockPos, final BlockState blockState) {
        state = blockState;
        pos = blockPos;
    }

    public void place(final ServerLevel serverLevel, final Vec3i spawnLocation) {
        serverLevel.setBlockAndUpdate(pos.offset(spawnLocation), state);
        //serverLevel.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos.offset(spawnLocation), Block.getId(state));
        //serverLevel.levelEvent(state.getSoundType().getPlaceSound().hashCode(), pos.offset(spawnLocation), Block.getId(state));
    }

    public BlockState getState() {
        return state;
    }
}