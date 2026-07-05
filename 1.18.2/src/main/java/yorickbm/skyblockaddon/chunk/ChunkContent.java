package yorickbm.skyblockaddon.chunk;

import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.function.Predicate;

public final class ChunkContent {
    private ChunkContent() {}

    public static boolean hasAnyContent(final ChunkAccess chunk) {
        return hasAnyContent(chunk.getSections(), section -> !section.hasOnlyAir());
    }

    static <T> boolean hasAnyContent(final T[] sections, final Predicate<T> hasContent) {
        for (final T section : sections) {
            if (section != null && hasContent.test(section)) return true;
        }
        return false;
    }
}
