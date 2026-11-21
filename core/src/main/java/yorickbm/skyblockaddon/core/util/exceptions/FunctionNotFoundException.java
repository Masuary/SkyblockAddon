package yorickbm.skyblockaddon.core.util.exceptions;

import java.util.UUID;

public class FunctionNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FunctionNotFoundException(final UUID uuid) {
        super("The function for '"+uuid+"' is not found!");
    }
}
