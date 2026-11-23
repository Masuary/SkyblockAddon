package yorickbm.skyblockaddon.util;

import iskallia.vault.item.crystal.VaultCrystalItem;
import net.minecraft.world.item.ItemStack;

/**
 * Vault Hunters classes; load only when the mod is installed.
 */
final class VaultCrystals {
    private VaultCrystals() {
    }

    static boolean isVaultCrystal(final ItemStack stack) {
        return stack.getItem() instanceof VaultCrystalItem;
    }
}
