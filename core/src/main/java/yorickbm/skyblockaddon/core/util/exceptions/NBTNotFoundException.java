package yorickbm.skyblockaddon.core.util.exceptions;

public class NBTNotFoundException extends RuntimeException {
    public NBTNotFoundException() {
        super("NBT not loaded correctly, resulting in failure.");
    }
}
