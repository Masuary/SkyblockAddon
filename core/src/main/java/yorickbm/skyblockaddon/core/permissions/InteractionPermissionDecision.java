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
     * is denied by any lacked permission that has block rules. {@link PermissionTriggers#ON_BREAK_BLOCK} (fired for
     * area tools such as gadgets, never with a single target) is judged the same way without a target block. Other
     * triggers judge the target block only and skip the block rules when there is none.
     * <p>
     * A placement that places no block (a non-block item used on a block, an entity placer) is not judged by a
     * permission whose item rules carve the hand item out: that item belongs to another permission on the trigger
     * (spawn eggs to the spawn egg toggle, not to Place blocks).
     */
    public static Optional<Permission> findDenyingPermission(final String trigger, final List<Permission> triggerPermissions,
                                                             final Predicate<String> groupHasPermission, final InteractionSubject subject) {
        final boolean placement = PermissionTriggers.ON_PLACE_BLOCK.equals(trigger);
        final Optional<String> blockSubject = placement
                ? subject.heldBlockId().or(subject::targetBlockId)
                : subject.targetBlockId();
        final boolean untargetedTrigger = placement || PermissionTriggers.ON_BREAK_BLOCK.equals(trigger);
        final boolean untargetedWithoutBlockSubject = untargetedTrigger && blockSubject.isEmpty();
        final Optional<String> itemLeftToItsOwnPermission = placement && subject.heldBlockId().isEmpty() ? subject.handItemId() : Optional.empty();

        for (final Permission permission : triggerPermissions) {
            if (groupHasPermission.test(permission.getId())) continue;

            final List<String> itemRules = permission.getData().getItemsData();
            final List<String> blockRules = permission.getData().getBlocksData();
            if (itemRules.isEmpty() && blockRules.isEmpty()) return Optional.of(permission);
            if (itemLeftToItsOwnPermission.filter(handItemId -> PermissionManager.isCarvedOut(itemRules, handItemId)).isPresent()) continue;

            final boolean itemDenied = !itemRules.isEmpty() && subject.handItemId()
                    .map(handItemId -> PermissionManager.checkMatch(itemRules, handItemId) == MatchResult.BLOCK)
                    .orElse(false);
            final boolean blockDenied = !blockRules.isEmpty() && (untargetedWithoutBlockSubject || blockSubject
                    .map(blockId -> PermissionManager.checkMatch(blockRules, blockId) == MatchResult.BLOCK)
                    .orElse(false));
            if (itemDenied || blockDenied) return Optional.of(permission);
        }
        return Optional.empty();
    }

    /**
     * A right click on an entity, judged on the entity's type id and the hand item. A lacked permission denies when its
     * entity rules match the entity (registries written before entity rules existed listed entity ids under
     * {@code blocks}, so those are used when a permission has no entity rules), when its item rules match the hand item,
     * or outright when it has neither.
     */
    public static Optional<Permission> findDenyingEntityInteraction(final List<Permission> triggerPermissions, final Predicate<String> groupHasPermission,
                                                                    final String entityTypeId, final Optional<String> handItemId) {
        for (final Permission permission : triggerPermissions) {
            if (groupHasPermission.test(permission.getId())) continue;

            final List<String> entityRules = permission.getData().getEntitiesData().isEmpty()
                    ? permission.getData().getBlocksData()
                    : permission.getData().getEntitiesData();
            final List<String> itemRules = permission.getData().getItemsData();
            if (entityRules.isEmpty() && itemRules.isEmpty()) return Optional.of(permission);

            final boolean entityDenied = !entityRules.isEmpty() && PermissionManager.checkMatch(entityRules, entityTypeId) == MatchResult.BLOCK;
            final boolean itemDenied = !itemRules.isEmpty() && handItemId
                    .map(itemId -> PermissionManager.checkMatch(itemRules, itemId) == MatchResult.BLOCK)
                    .orElse(false);
            if (entityDenied || itemDenied) return Optional.of(permission);
        }
        return Optional.empty();
    }

    /**
     * An item that puts an entity into the world (spawn egg, armor stand, frame, painting, boat, minecart, end crystal)
     * is judged as a placement of that item with no target block, wherever it is used: block carve-outs and the clicked
     * block never let it through, and without a block subject any lacked placement permission with block rules denies it.
     */
    public static Optional<Permission> findDenyingEntityPlacement(final List<Permission> placePermissions,
                                                                  final Predicate<String> groupHasPermission, final String placerItemId) {
        return findDenyingPermission(PermissionTriggers.ON_PLACE_BLOCK, placePermissions, groupHasPermission,
                new InteractionSubject(Optional.of(placerItemId), Optional.empty(), Optional.empty()));
    }
}
