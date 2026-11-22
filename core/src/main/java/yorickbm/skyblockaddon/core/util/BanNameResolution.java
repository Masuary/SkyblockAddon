package yorickbm.skyblockaddon.core.util;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Decides whom /island ban bans for a typed name that is not online. A known offline-mode player (version 3 UUID) is
 * banned without a lookup. A known Mojang account (version 4 UUID) is confirmed at Mojang because the name may have
 * changed hands since that player last joined; the known player is only used when Mojang cannot be reached.
 */
public final class BanNameResolution {

    public enum RemoteLookup { NOT_ASKED, FOUND, NOT_FOUND, FAILED }

    public enum Reply { BAN, UNKNOWN_PLAYER, LOOKUP_FAILED }

    public record Decision(Reply reply, Optional<UUID> banTarget) {
    }

    private static final Pattern MINECRAFT_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int MOJANG_ACCOUNT_UUID_VERSION = 4;

    private BanNameResolution() {
    }

    public static boolean isPossibleName(final String typedName) {
        return MINECRAFT_NAME.matcher(typedName).matches();
    }

    public static boolean needsRemoteLookup(final Optional<UUID> knownPlayer) {
        return knownPlayer.isEmpty() || knownPlayer.get().version() == MOJANG_ACCOUNT_UUID_VERSION;
    }

    public static Decision decide(final Optional<UUID> knownPlayer, final RemoteLookup remoteLookup, final Optional<UUID> remotePlayer) {
        return switch (remoteLookup) {
            case FOUND -> new Decision(Reply.BAN, remotePlayer);
            case NOT_FOUND -> new Decision(Reply.UNKNOWN_PLAYER, Optional.empty());
            case FAILED -> knownPlayer.isPresent() ? new Decision(Reply.BAN, knownPlayer) : new Decision(Reply.LOOKUP_FAILED, Optional.empty());
            case NOT_ASKED -> knownPlayer.isPresent() ? new Decision(Reply.BAN, knownPlayer) : new Decision(Reply.UNKNOWN_PLAYER, Optional.empty());
        };
    }
}
