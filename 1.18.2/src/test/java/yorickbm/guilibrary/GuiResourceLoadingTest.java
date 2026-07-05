package yorickbm.guilibrary;

import org.junit.jupiter.api.Test;
import yorickbm.guilibrary.JSON.GUIJson;
import yorickbm.guilibrary.util.JSON.JSONEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GuiResourceLoadingTest {
    @Test
    void everyBundledFallbackGuiResolvesAllConfiguredClasses() throws Exception {
        final Path guiDirectory = Path.of(
                "../core/src/main/resources/assets/skyblockaddon/guis"
        );
        final List<Path> guiFiles;
        try (final var paths = Files.list(guiDirectory)) {
            guiFiles = paths.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }

        assertEquals(11, guiFiles.size());
        for (final Path guiFile : guiFiles) {
            final GUIJson gui = JSONEncoder.loadFromFile(guiFile, GUIJson.class);
            assertFalse(gui.getKey().isBlank(), guiFile::toString);
            assertFalse(gui.getTitle().isEmpty(), guiFile::toString);
            gui.validateConfiguredClasses();
        }
    }
}
