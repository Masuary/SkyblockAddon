package yorickbm.skyblockaddon.core.registries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionGroupRegistryTest {
    private final PermissionGroupRegistry registry = PermissionGroupRegistry.getInstance();

    @BeforeEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void expandsNestedGroupReferences(@TempDir final Path directory) throws IOException {
        writeGroups(directory, """
                {
                  "groups": {
                    "storage": {"block": ["minecraft:chest"]},
                    "all_storage": {"block": ["#storage", "minecraft:barrel"]}
                  }
                }
                """);
        assertEquals(2, registry.loadFromDirectory(directory, ignored -> true));

        assertEquals(
                List.of("minecraft:chest", "minecraft:barrel"),
                registry.expandPatterns("block", List.of("#all_storage"))
        );
    }

    @Test
    void rejectsCyclicGroupReferences(@TempDir final Path directory) throws IOException {
        writeGroups(directory, """
                {
                  "groups": {
                    "first": {"block": ["#second"]},
                    "second": {"block": ["#first"]}
                  }
                }
                """);
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.loadFromDirectory(directory, ignored -> true)
        );
        assertTrue(exception.getMessage().contains("Cyclic permission group reference"));
        assertTrue(exception.getMessage().contains("first"));
        assertTrue(exception.getMessage().contains("second"));
    }

    @Test
    void failedReloadPreservesPreviouslyLoadedGroups(@TempDir final Path directory) throws IOException {
        final Path validDirectory = Files.createDirectory(directory.resolve("valid"));
        writeGroups(validDirectory, "{\"groups\":{\"valid\":{\"block\":[\"minecraft:chest\"]}}}");
        registry.loadFromDirectory(validDirectory, ignored -> true);

        final Path invalidDirectory = Files.createDirectory(directory.resolve("invalid"));
        writeGroups(invalidDirectory, "{\"groups\":{\"broken\":{\"block\":[\"#missing\"]}}}");
        assertThrows(IllegalArgumentException.class,
                () -> registry.loadFromDirectory(invalidDirectory, ignored -> true));

        assertEquals(List.of("minecraft:chest"), registry.expandPatterns("block", List.of("#valid")));
    }

    @Test
    void rejectsUnknownGroupReference(@TempDir final Path directory) throws IOException {
        writeGroups(directory, "{\"groups\":{}}");
        registry.loadFromDirectory(directory, ignored -> true);

        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.expandPatterns("block", List.of("#typo"))
        );
        assertTrue(exception.getMessage().contains("#typo"));
    }

    @Test
    void ignoresGroupDeclaredOnlyByAbsentOptionalMod(@TempDir final Path directory) throws IOException {
        writeGroups(directory, """
                {
                  "mod": "optional_mod",
                  "groups": {
                    "optional_blocks": {"block": ["optional_mod:machine"]}
                  }
                }
                """);
        registry.loadFromDirectory(directory, ignored -> false);

        assertEquals(List.of(), registry.expandPatterns("block", List.of("#optional_blocks")));
    }

    private void writeGroups(final Path directory, final String json) throws IOException {
        Files.writeString(directory.resolve("groups.json"), json);
    }
}
