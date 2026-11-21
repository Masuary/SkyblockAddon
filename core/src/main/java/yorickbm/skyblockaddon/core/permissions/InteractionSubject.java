package yorickbm.skyblockaddon.core.permissions;

import java.util.Objects;
import java.util.Optional;

/**
 * What a block interaction is judged on, as registry ids: the item in the acting hand, the block that item would place
 * (for block items) and the block the interaction targets (the clicked block, or the block a placement produced).
 */
public record InteractionSubject(Optional<String> handItemId, Optional<String> heldBlockId, Optional<String> targetBlockId) {
    public InteractionSubject {
        Objects.requireNonNull(handItemId, "handItemId");
        Objects.requireNonNull(heldBlockId, "heldBlockId");
        Objects.requireNonNull(targetBlockId, "targetBlockId");
    }
}
