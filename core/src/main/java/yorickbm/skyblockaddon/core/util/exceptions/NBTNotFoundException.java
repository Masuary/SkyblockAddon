package yorickbm.skyblockaddon.core.util.exceptions;

public class NBTNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NBTNotFoundException() {
        super("NBT not loaded correctly, resulting in failure.");
    }
}
