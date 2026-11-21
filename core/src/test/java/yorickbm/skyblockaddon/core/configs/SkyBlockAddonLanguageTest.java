package yorickbm.skyblockaddon.core.configs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkyBlockAddonLanguageTest {
    @TempDir
    Path temporaryFolder;

    @Test
    void serverFileOverridesAndBundledDefaultsFillMissingKeys() throws IOException {
        final Path serverLanguage = temporaryFolder.resolve("language.json");
        Files.writeString(serverLanguage, "{\"commands.has.no.island\": \"Custom server text\"}");

        SkyBlockAddonLanguage.loadLocalization(serverLanguage);

        assertEquals("Custom server text", SkyBlockAddonLanguage.getLocalizedString("commands.has.no.island"));
        assertNotEquals("commands.create.already", SkyBlockAddonLanguage.getLocalizedString("commands.create.already"));
    }

    @Test
    void malformedServerFileFails() throws IOException {
        final Path serverLanguage = temporaryFolder.resolve("language.json");
        Files.writeString(serverLanguage, "{\"broken\": ");

        assertThrows(IllegalStateException.class, () -> SkyBlockAddonLanguage.loadLocalization(serverLanguage));
    }
}
