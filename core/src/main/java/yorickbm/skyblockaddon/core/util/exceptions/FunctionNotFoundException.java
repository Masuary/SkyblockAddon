package yorickbm.skyblockaddon.core.util.exceptions;

import java.util.UUID;

public class FunctionNotFoundException extends RuntimeException {
    public FunctionNotFoundException(final UUID uuid) {
        super("The function for '"+uuid+"' is not found!");
    }
}
