package yorickbm.skyblockaddon.core.util.exceptions;

public class TerralithFoundException extends RuntimeException {
    public TerralithFoundException() {
        super("Terralith has been detected. Remove Terralith before using SkyblockAddon because it can replace void-world generation and corrupt the island world.");
    }
}
