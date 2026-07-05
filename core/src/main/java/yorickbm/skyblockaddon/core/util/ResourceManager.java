package yorickbm.skyblockaddon.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.configs.VoidProtectionConfig;
import yorickbm.skyblockaddon.core.util.exceptions.ResourceNotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;


public class ResourceManager {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String[] OLD_PERMISSION_FILES = {
        "general", "storage", "transport", "redstone", "interactables", "admin"
    };
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();


    /**
     * Generate resource file from projects resources folder
     * @param file Path for file destination
     * @param asset File name
     * @throws ResourceNotFoundException If asset is not within projects resources
     */
    private static void generateFile(final Path FMLPath, final String file, final String asset) throws ResourceNotFoundException {
        final File resourceFile = new File(FMLPath.resolve(SkyblockAddonCore.MOD_ID) + "/" +  file);

        try {
            //Determine if file doesnt exists
            if (!resourceFile.exists()) {
                if (resourceFile.createNewFile()) {
                    try (final InputStream in = SkyblockAddonCore.class.getResourceAsStream("/assets/" + SkyblockAddonCore.MOD_ID + "/" + asset)) {
                        if (in == null) {
                            throw new ResourceNotFoundException(asset);
                        }

                        //Copy asset into file
                        Files.copy(in, resourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(resourceFile.getPath() + "\n" + e);
        }
    }

    public static void commonSetup(final Path FMLPath) throws ResourceNotFoundException {
        backupConfigurationDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID));
        //ResourceManager.getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID), SkyblockAddonCore.MOD_ID);

        //Custom island.nbt
        generateFile(FMLPath, "island.nbt", "structures/island.nbt");

        //Custom language.json
        generateFile(FMLPath, "language.json", "lang/en_us.json");
        mergeBundledLanguageKeys(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/language.json"));
        SkyBlockAddonLanguage.loadLocalization(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/language.json"));

        //Generate registries
        if (!Files.exists(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/"))) {
            ResourceManager.getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/"), "registries");
        }
        if(!Files.exists(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/BiomeRegistry.json"))) {
            generateFile(FMLPath, "registries/BiomeRegistry.json", "registries/BiomeRegistry.json");
        }

        //Generate permission + group files
        getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/"), "permissions");
        getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/"), "groups");

        migrateOldPermissionFiles(FMLPath);

        extractResourceDirectory(FMLPath, "registries/permissions");
        extractResourceDirectory(FMLPath, "registries/groups");


        //Generate void protection config
        generateFile(FMLPath, "void_protection.json", "void_protection.json");
        VoidProtectionConfig.getInstance().load(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/void_protection.json"));

        // Extract GUI files (each individually — only creates if missing, so server edits survive)
        getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/"), "guis");
        generateFile(FMLPath, "guis/overview.json", "guis/overview.json");
        generateFile(FMLPath, "guis/settings.json", "guis/settings.json");
        generateFile(FMLPath, "guis/biomes.json", "guis/biomes.json");
        generateFile(FMLPath, "guis/travel.json", "guis/travel.json");
        generateFile(FMLPath, "guis/members.json", "guis/members.json");
        generateFile(FMLPath, "guis/groups.json", "guis/groups.json");
        generateFile(FMLPath, "guis/set_group.json", "guis/set_group.json");
        generateFile(FMLPath, "guis/set_permission.json", "guis/set_permission.json");
        generateFile(FMLPath, "guis/members_group.json", "guis/members_group.json");
        generateFile(FMLPath, "guis/permissions.json", "guis/permissions.json");
        generateFile(FMLPath, "guis/confirm_setspawn.json", "guis/confirm_setspawn.json");

        warnAboutLegacyConfiguration(FMLPath.resolve(SkyblockAddonCore.MOD_ID));
    }

    /**
     * Dynamically discovers and extracts all JSON files from a resource directory inside the JAR,
     * so no hardcoded file list is needed — whatever is packaged gets deployed.
     *
     * Strategy:
     *  1. Try Paths.get(uri) — works for file: (dev) and union: (Forge production, which registers
     *     its own NIO filesystem provider at startup).
     *  2. Fall back to JarFile scanning for standard jar: URIs (non-Forge / test environments).
     */
    private static void extractResourceDirectory(final Path FMLPath, final String resourceSubDir) {
        final String resourcePath = "/assets/" + SkyblockAddonCore.MOD_ID + "/" + resourceSubDir + "/";
        final URL dirUrl = SkyblockAddonCore.class.getResource(resourcePath);
        if (dirUrl == null) {
            LOGGER.warn("Resource directory not found: {}", resourcePath);
            return;
        }
        LOGGER.debug("Scanning resource directory '{}' via {} URL", resourceSubDir, dirUrl.getProtocol());

        try {
            final URI dirUri = dirUrl.toURI();

            // Primary: NIO path — covers file: (dev) and union: (Forge production)
            try {
                final Path dirPath = Paths.get(dirUri);
                try (final Stream<Path> listing = Files.list(dirPath)) {
                    listing.filter(p -> p.getFileName().toString().endsWith(".json"))
                           .forEach(p -> extractSingleFile(FMLPath, resourceSubDir, p.getFileName().toString()));
                }
                return;
            } catch (final FileSystemNotFoundException ignored) {
                // Not a registered NIO filesystem — fall through to JarFile fallback
            }

            // Fallback: standard jar: protocol (non-Forge environments)
            if ("jar".equals(dirUrl.getProtocol())) {
                final String jarFilePath = URLDecoder.decode(
                        dirUrl.getPath().substring(5, dirUrl.getPath().indexOf("!")),
                        StandardCharsets.UTF_8);
                final String prefix = "assets/" + SkyblockAddonCore.MOD_ID + "/" + resourceSubDir + "/";
                try (final JarFile jar = new JarFile(jarFilePath)) {
                    jar.stream()
                       .filter(e -> !e.isDirectory() && e.getName().startsWith(prefix) && e.getName().endsWith(".json"))
                       .forEach(e -> extractSingleFile(FMLPath, resourceSubDir, e.getName().substring(prefix.length())));
                }
                return;
            }

            throw new IllegalStateException("Unhandled resource URL protocol '" + dirUrl.getProtocol()
                    + "' for directory '" + resourceSubDir + "'");

        } catch (final IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to scan resource directory '" + resourceSubDir + "'", e);
        }
    }

    private static void extractSingleFile(final Path FMLPath, final String resourceSubDir, final String fileName) {
        try {
            generateFile(FMLPath, resourceSubDir + "/" + fileName, resourceSubDir + "/" + fileName);
        } catch (final ResourceNotFoundException ex) {
            throw new IllegalStateException("Bundled resource disappeared while extracting "
                    + resourceSubDir + "/" + fileName, ex);
        }
    }

    private static void mergeBundledLanguageKeys(final Path languageFile) {
        try (final InputStream bundledStream = SkyblockAddonCore.class.getResourceAsStream(
                "/assets/" + SkyblockAddonCore.MOD_ID + "/lang/en_us.json")) {
            if (bundledStream == null) throw new ResourceNotFoundException("lang/en_us.json");

            final java.lang.reflect.Type mapType = new TypeToken<LinkedHashMap<String, String>>() { }.getType();
            final Map<String, String> bundled;
            try (final Reader reader = new InputStreamReader(bundledStream, StandardCharsets.UTF_8)) {
                bundled = PRETTY_GSON.fromJson(reader, mapType);
            }
            final Map<String, String> deployed;
            try (final Reader reader = Files.newBufferedReader(languageFile, StandardCharsets.UTF_8)) {
                deployed = PRETTY_GSON.fromJson(reader, mapType);
            }

            final Map<String, String> merged = new LinkedHashMap<>(bundled);
            if (deployed != null) merged.putAll(deployed);
            if (deployed != null && deployed.keySet().containsAll(bundled.keySet())) return;

            createBackupOnce(languageFile);
            writeJsonAtomically(languageFile, PRETTY_GSON.toJson(merged));
            LOGGER.info("Added {} missing bundled language keys to {}",
                    bundled.keySet().stream().filter(key -> deployed == null || !deployed.containsKey(key)).count(),
                    languageFile);
        } catch (final IOException | ResourceNotFoundException exception) {
            throw new IllegalStateException("Failed to merge bundled language keys into " + languageFile, exception);
        }
    }

    private static void createBackupOnce(final Path source) throws IOException {
        final Path backup = source.resolveSibling(source.getFileName() + ".pre-10.0.bak");
        if (!Files.exists(backup)) Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void backupConfigurationDirectory(final Path sourceDirectory) {
        if (!Files.isDirectory(sourceDirectory)) return;

        final Path backupDirectory = sourceDirectory.resolveSibling(
                sourceDirectory.getFileName() + ".pre-10.0-backup"
        );
        if (Files.exists(backupDirectory)) return;
        final Path temporaryBackupDirectory = backupDirectory.resolveSibling(
                backupDirectory.getFileName() + ".tmp"
        );

        try {
            deleteDirectoryIfExists(temporaryBackupDirectory);
            try (final Stream<Path> paths = Files.walk(sourceDirectory)) {
                for (final Path source : paths.sorted().toList()) {
                    final Path destination = temporaryBackupDirectory.resolve(sourceDirectory.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
            AtomicFileMover.moveWithoutReplacement(temporaryBackupDirectory, backupDirectory);
            LOGGER.info("Created pre-10.0 configuration backup at {}", backupDirectory);
        } catch (final IOException exception) {
            try {
                deleteDirectoryIfExists(temporaryBackupDirectory);
            } catch (final IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new IllegalStateException("Failed to back up configuration directory " + sourceDirectory, exception);
        }
    }

    private static void deleteDirectoryIfExists(final Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (final Stream<Path> paths = Files.walk(directory)) {
            for (final Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void writeJsonAtomically(final Path destination, final String json) throws IOException {
        final Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, json + System.lineSeparator(), StandardCharsets.UTF_8);
        AtomicFileMover.moveReplacing(temporary, destination);
    }

    private static void warnAboutLegacyConfiguration(final Path modDirectory) {
        final Path legacyPermissions = modDirectory.resolve("registries/PermissionRegistry.json");
        if (Files.isRegularFile(legacyPermissions)) {
            LOGGER.warn("Legacy permission registry {} is active as an override. New per-mod permissions "
                    + "are merged by ID; migrate this file before removing the compatibility layer.", legacyPermissions);
        }

        final Path guiDirectory = modDirectory.resolve("guis");
        if (!Files.isDirectory(guiDirectory)) return;
        try (final Stream<Path> paths = Files.list(guiDirectory)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .filter(ResourceManager::containsLegacyLore)
                    .forEach(path -> LOGGER.warn("Legacy string-encoded lore detected in {}; compatibility parsing is active.", path));
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to inspect GUI configuration in " + guiDirectory, exception);
        }
    }

    private static boolean containsLegacyLore(final Path path) {
        try (final Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return containsLegacyLore(JsonParser.parseReader(reader), false);
        } catch (final IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to inspect legacy lore in " + path, exception);
        }
    }

    private static boolean containsLegacyLore(final JsonElement element, final boolean insideLore) {
        if (element == null || element.isJsonNull()) return false;
        if (insideLore && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return true;
        if (element.isJsonArray()) {
            for (final JsonElement child : element.getAsJsonArray()) {
                if (containsLegacyLore(child, insideLore)) return true;
            }
            return false;
        }
        if (!element.isJsonObject()) return false;

        for (final Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (containsLegacyLore(entry.getValue(), entry.getKey().equals("lore"))) return true;
        }
        return false;
    }

    /**
     * Removes old category-based permission files when migrating to per-mod structure.
     * Only runs if minecraft.json (new structure marker) does not yet exist.
     */
    private static void migrateOldPermissionFiles(final Path FMLPath) {
        final Path permDir = FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/permissions/");
        if (Files.exists(permDir.resolve("minecraft.json"))) return; // already migrated

        final Path backupDirectory = permDir.resolve("legacy-category-backup");
        boolean anyMoved = false;
        for (final String name : OLD_PERMISSION_FILES) {
            final Path old = permDir.resolve(name + ".json");
            if (!Files.isRegularFile(old)) continue;
            try {
                Files.createDirectories(backupDirectory);
                Files.move(old, backupDirectory.resolve(old.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                anyMoved = true;
                LOGGER.info("Moved legacy permission file {} to {}", old.getFileName(), backupDirectory);
            } catch (final IOException exception) {
                throw new IllegalStateException("Failed to preserve legacy permission file " + old, exception);
            }
        }
        if (anyMoved) {
            LOGGER.info("Migrated permissions to per-mod structure.");
        }
    }

    /**
     * Ensures the child directory exists inside the parent directory.
     *
     * @param parent the parent Path
     * @param child  the child folder name
     * @throws RuntimeException if creation fails
     */
    public static void getOrCreateDirectory(Path parent, String child) {
        Path dir = parent.resolve(child);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory: " + dir, e);
        }
    }
}
