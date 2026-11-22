package yorickbm.skyblockaddon.core.util;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.util.exceptions.ResourceNotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ResourceManager {
    public static final String ISLAND_TEMPLATE_FILE = "island.nbt";
    private static final List<String> BUNDLED_GUI_FILES = List.of(
            "overview.json", "settings.json", "biomes.json", "travel.json", "members.json", "groups.json",
            "set_group.json", "permissions.json", "set_permission.json", "members_group.json", "confirm_setspawn.json", "confirm_move_to_visitors.json",
            "visitors.json", "bans.json", "transfer.json");

    /**
     * Generate resource file from projects resources folder
     * @param file Path for file destination
     * @param asset File name
     * @return If the file was missing and has been generated
     * @throws ResourceNotFoundException If asset is not within projects resources
     */
    private static boolean generateFile(final Path FMLPath, final String file, final String asset) throws ResourceNotFoundException {
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
                        return true;
                    }
                }
            }
            return false;
        } catch (final IOException e) {
            throw new RuntimeException(resourceFile.getPath() + "\n" + e);
        }
    }

    private static void generateMissingFile(final List<String> generatedFiles, final Path FMLPath, final String file, final String asset) {
        if (generateFile(FMLPath, file, asset)) generatedFiles.add(file);
    }

    /**
     * Generate every missing config file from the bundled copy of the loaded pack.
     * @return The generated files, relative to the mod's config folder
     */
    public static List<String> commonSetup(Path FMLPath, BundledDefaultsPack pack) throws ResourceNotFoundException {
        final List<String> generatedFiles = new ArrayList<>();
        generateMissingFile(generatedFiles, FMLPath, ISLAND_TEMPLATE_FILE, pack.assetFor("structures/island.nbt"));

        //Custom language.json
        generateMissingFile(generatedFiles, FMLPath, "language.json", pack.assetFor("lang/en_us.json"));
        SkyBlockAddonLanguage.loadLocalization(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/language.json"));

        //Generate registries
        if (!Files.exists(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/"))) {
            ResourceManager.getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/"), "registries");
        }
        if(!Files.exists(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/BiomeRegistry.json"))) {
            generateMissingFile(generatedFiles, FMLPath, "registries/BiomeRegistry.json", pack.assetFor("registries/BiomeRegistry.json"));
        }

        generateMissingFile(generatedFiles, FMLPath, "registries/PermissionRegistry.json", pack.permissionRegistryAsset());

        //Generate GUIS: every bundled GUI missing from the server's folder is added, existing files are kept
        ResourceManager.getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/"), "guis");
        for (final String guiFile : BUNDLED_GUI_FILES) {
            generateMissingFile(generatedFiles, FMLPath, "guis/" + guiFile, pack.assetFor("guis/" + guiFile));
        }
        return List.copyOf(generatedFiles);
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
        } catch (final IOException creationFailure) {
            throw new IllegalStateException("Failed to create config folder '" + dir.toAbsolutePath() + "'", creationFailure);
        }
    }
}
