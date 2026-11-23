package yorickbm.skyblockaddon.core.permissions;

import java.util.Objects;
import java.util.Optional;

/**
 * What a block interaction is judged on, as registry ids: the item in the acting hand, the block being placed (the block
 * a held block item would place, or the block a placement produced) and the block the interaction targets (the clicked
 * block, or the block a placement produced).
 */
public record InteractionSubject(Optional<String> handItemId, Optional<String> heldBlockId, Optional<String> targetBlockId) {
    public InteractionSubject {
        Objects.requireNonNull(handItemId, "handItemId");
        Objects.requireNonNull(heldBlockId, "heldBlockId");
        Objects.requireNonNull(targetBlockId, "targetBlockId");
    }
}
