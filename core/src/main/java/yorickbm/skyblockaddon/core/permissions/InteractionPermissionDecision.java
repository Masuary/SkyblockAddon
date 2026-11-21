package yorickbm.skyblockaddon.core.permissions;

import yorickbm.skyblockaddon.core.util.MatchResult;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class InteractionPermissionDecision {

    private InteractionPermissionDecision() {
    }

    /**
     * Find the first permission on the trigger that the group lacks and whose rules deny the interaction. A lacked
     * permission without item and block rules denies outright; otherwise the hand item is matched against the item
     * rules and the block subject against the block rules, and either match denies.
     * <p>
     * For {@link PermissionTriggers#ON_PLACE_BLOCK} the block subject is the held block when a block item is held (so
     * carve-outs follow what is being placed, not what was clicked), else the target block; a placement with neither
     * is denied by any lacked permission that has block rules. Other triggers judge the target block only and skip the
     * block rules when there is none.
     */
    public static Optional<Permission> findDenyingPermission(final String trigger, final List<Permission> triggerPermissions,
                                                             final Predicate<String> groupHasPermission, final InteractionSubject subject) {
        final Optional<String> blockSubject = PermissionTriggers.ON_PLACE_BLOCK.equals(trigger)
                ? subject.heldBlockId().or(subject::targetBlockId)
                : subject.targetBlockId();
        final boolean placementWithoutBlockSubject = PermissionTriggers.ON_PLACE_BLOCK.equals(trigger) && blockSubject.isEmpty();

        for (final Permission permission : triggerPermissions) {
            if (groupHasPermission.test(permission.getId())) continue;

            final List<String> itemRules = permission.getData().getItemsData();
            final List<String> blockRules = permission.getData().getBlocksData();
            if (itemRules.isEmpty() && blockRules.isEmpty()) return Optional.of(permission);

            final boolean itemDenied = !itemRules.isEmpty() && subject.handItemId()
                    .map(handItemId -> PermissionManager.checkMatch(itemRules, handItemId) == MatchResult.BLOCK)
                    .orElse(false);
            final boolean blockDenied = !blockRules.isEmpty() && (placementWithoutBlockSubject || blockSubject
                    .map(blockId -> PermissionManager.checkMatch(blockRules, blockId) == MatchResult.BLOCK)
                    .orElse(false));
            if (itemDenied || blockDenied) return Optional.of(permission);
        }
        return Optional.empty();
    }
}
