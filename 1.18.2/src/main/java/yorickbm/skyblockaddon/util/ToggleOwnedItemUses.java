package yorickbm.skyblockaddon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraftforge.fml.ModList;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Item uses on a block that one named toggle judges alone on the click, although the click would otherwise count as a
 * placement: bone meal on a block it can grow ("Use bone meal"), flint and steel on obsidian where the fire forms a
 * nether portal ("Nether portals") and a vault crystal on any block ("Vault portals"; the crystal has no other use on a
 * block). The blocks such a use places in the same click (plants, fire and portal blocks) are not judged as placements
 * either.
 */
public final class ToggleOwnedItemUses {
    private static final boolean VAULT_HUNTERS_LOADED = ModList.get().isLoaded("the_vault");
    private static final Map<ServerPlayer, AllowedUse> ALLOWED_USE_THIS_TICK = new WeakHashMap<>();

    public enum Owner {
        BONE_MEAL(PermissionTriggers.ON_BONEMEAL),
        NETHER_PORTAL_IGNITION(PermissionTriggers.ON_PORTAL_IGNITION),
        VAULT_PORTAL_OPENING(PermissionTriggers.ON_OPEN_VAULT_PORTAL);

        private final String trigger;

        Owner(final String trigger) {
            this.trigger = trigger;
        }

        public String trigger() {
            return trigger;
        }
    }

    private record AllowedUse(ItemStack usedStack, long gameTime) {
    }

    private ToggleOwnedItemUses() {
    }

    /**
     * The toggle that owns the use of the stack on the clicked block. A click on a block with its own use (not a
     * placement) is owned only by bone meal on a growable that hands that click on to the item.
     */
    public static Optional<Owner> ownerOf(final ServerLevel level, final BlockPos clickedPosition, final Direction clickedFace, final ItemStack usedStack,
                                          final boolean clickIsPlacement) {
        final BlockState clickedState = level.getBlockState(clickedPosition);
        if (isGrownByBoneMeal(level, clickedPosition, clickedState, usedStack) && (clickIsPlacement || passesBoneMealToTheItem(clickedState, usedStack))) {
            return Optional.of(Owner.BONE_MEAL);
        }
        if (!clickIsPlacement) return Optional.empty();
        if (usedStack.is(Items.FLINT_AND_STEEL) && clickedState.is(Blocks.OBSIDIAN)
                && PortalShape.findEmptyPortalShape(level, clickedPosition.relative(clickedFace), Direction.Axis.X).isPresent()) {
            return Optional.of(Owner.NETHER_PORTAL_IGNITION);
        }
        if (isVaultCrystal(usedStack)) return Optional.of(Owner.VAULT_PORTAL_OPENING);
        return Optional.empty();
    }

    public static boolean isVaultCrystal(final ItemStack stack) {
        return VAULT_HUNTERS_LOADED && VaultCrystals.isVaultCrystal(stack);
    }

    private static boolean isGrownByBoneMeal(final ServerLevel level, final BlockPos clickedPosition, final BlockState clickedState, final ItemStack usedStack) {
        return usedStack.getItem() instanceof BoneMealItem && clickedState.getBlock() instanceof final BonemealableBlock growable
                && growable.isValidBonemealTarget(level, clickedPosition, clickedState, false);
    }

    /**
     * Growables whose own use returns PASS while bone meal can grow them, so the bone meal is what the click uses.
     */
    private static boolean passesBoneMealToTheItem(final BlockState clickedState, final ItemStack usedStack) {
        return clickedState.getBlock() instanceof CaveVines
                || clickedState.getBlock() instanceof SweetBerryBushBlock && usedStack.is(Items.BONE_MEAL);
    }

    /**
     * Must run on the server thread, like every block event that reads it.
     */
    public static void rememberAllowedUse(final ServerPlayer player, final ItemStack usedStack) {
        ALLOWED_USE_THIS_TICK.put(player, new AllowedUse(usedStack, player.getLevel().getGameTime()));
    }

    public static void forgetAllowedUse(final ServerPlayer player) {
        ALLOWED_USE_THIS_TICK.remove(player);
    }

    /**
     * Whether a block the player placed comes from the toggle-owned use allowed earlier in this tick: the same stack
     * (by identity, Forge restores it in place before the placement events) is still in one of the player's hands.
     */
    public static boolean isPlacedByAllowedUse(final ServerPlayer player) {
        final AllowedUse allowedUse = ALLOWED_USE_THIS_TICK.get(player);
        if (allowedUse == null || allowedUse.gameTime() != player.getLevel().getGameTime()) return false;
        for (final InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand) == allowedUse.usedStack()) return true;
        }
        return false;
    }
}
