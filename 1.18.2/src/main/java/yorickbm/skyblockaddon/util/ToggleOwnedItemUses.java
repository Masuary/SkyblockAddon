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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraftforge.fml.ModList;
import yorickbm.skyblockaddon.core.permissions.PermissionTriggers;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Item uses on a block that one named toggle judges alone, although the click would otherwise count as a placement:
 * bone meal on a block it can grow ("Use bone meal", judged later by the bonemeal event), flint and steel on obsidian
 * where the fire forms a nether portal ("Nether portals") and a vault crystal on a vault portal frame ("Vault portals"),
 * both judged on the click. The blocks such a use places in the same click (plants, fire and portal blocks) are not
 * judged as placements either.
 */
public final class ToggleOwnedItemUses {
    private static final boolean VAULT_HUNTERS_LOADED = ModList.get().isLoaded("the_vault");
    private static final Map<ServerPlayer, AllowedUse> ALLOWED_USE_THIS_TICK = new WeakHashMap<>();

    public enum Owner {
        BONE_MEAL(Optional.empty()),
        NETHER_PORTAL_IGNITION(Optional.of(PermissionTriggers.ON_PORTAL_IGNITION)),
        VAULT_PORTAL_OPENING(Optional.of(PermissionTriggers.ON_OPEN_VAULT_PORTAL));

        private final Optional<String> triggerJudgedOnTheClick;

        Owner(final Optional<String> triggerJudgedOnTheClick) {
            this.triggerJudgedOnTheClick = triggerJudgedOnTheClick;
        }

        public Optional<String> triggerJudgedOnTheClick() {
            return triggerJudgedOnTheClick;
        }
    }

    private record AllowedUse(ItemStack usedStack, long gameTime) {
    }

    private ToggleOwnedItemUses() {
    }

    public static Optional<Owner> ownerOf(final ServerLevel level, final BlockPos clickedPosition, final Direction clickedFace, final ItemStack usedStack) {
        final BlockState clickedState = level.getBlockState(clickedPosition);
        if (usedStack.getItem() instanceof BoneMealItem && clickedState.getBlock() instanceof final BonemealableBlock growable
                && growable.isValidBonemealTarget(level, clickedPosition, clickedState, false)) {
            return Optional.of(Owner.BONE_MEAL);
        }
        if (usedStack.is(Items.FLINT_AND_STEEL) && clickedState.is(Blocks.OBSIDIAN)
                && PortalShape.findEmptyPortalShape(level, clickedPosition.relative(clickedFace), Direction.Axis.X).isPresent()) {
            return Optional.of(Owner.NETHER_PORTAL_IGNITION);
        }
        if (VAULT_HUNTERS_LOADED && VaultPortalFrames.isCrystalOnFrame(level, clickedPosition, clickedState, usedStack)) {
            return Optional.of(Owner.VAULT_PORTAL_OPENING);
        }
        return Optional.empty();
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
