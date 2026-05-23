package yorickbm.skyblockaddon.core.util.exceptions;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(final String resource) {
        super("Resource not found '/assets/"+SkyblockAddonCore.MOD_ID+"/"+resource+"'!");
    }
}
