package yorickbm.skyblockaddon.util;

import iskallia.vault.block.VaultPortalBlock;
import iskallia.vault.item.crystal.VaultCrystalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vault Hunters classes; load only when the mod is installed.
 */
final class VaultPortalFrames {
    private VaultPortalFrames() {
    }

    static boolean isCrystalOnFrame(final ServerLevel level, final BlockPos clickedPosition, final BlockState clickedState, final ItemStack usedStack) {
        return usedStack.getItem() instanceof VaultCrystalItem && VaultPortalBlock.FRAME.test(clickedState, level, clickedPosition);
    }
}
