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

public class ResourceManager {

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

    public static void commonSetup(Path FMLPath, RegistrySelector selector) throws ResourceNotFoundException {
        //ResourceManager.getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID), SkyblockAddonCore.MOD_ID);

        //Custom island.nbt
        generateFile(FMLPath, "island.nbt", "structures/island.nbt");

        //Custom language.json
        generateFile(FMLPath, "language.json", "lang/en_us.json");
        SkyBlockAddonLanguage.loadLocalization(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/language.json"));

        //Generate registries
        if (!Files.exists(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/"))) {
            ResourceManager.getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/"), "registries");
        }
        if(!Files.exists(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/registries/BiomeRegistry.json"))) {
            generateFile(FMLPath, "registries/BiomeRegistry.json", "registries/BiomeRegistry.json");
        }

        //Generate correct permissionRegistry
        try {
            String path = selector.getRegistry("PermissionRegistry");
            generateFile(FMLPath, "registries/PermissionRegistry.json", path);
        } catch(IllegalArgumentException ex) {
            throw new ResourceNotFoundException("registries/PermissionRegistry.json");
        }


        //Generate GUIS
        if (!Files.exists(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/guis/"))) {
            ResourceManager.getOrCreateDirectory(FMLPath.resolve(SkyblockAddonCore.MOD_ID + "/"), "guis");

            generateFile(FMLPath, "guis/overview.json", "guis/overview.json");
            generateFile(FMLPath, "guis/settings.json", "guis/settings.json");
            generateFile(FMLPath, "guis/biomes.json", "guis/biomes.json");
            generateFile(FMLPath, "guis/travel.json", "guis/travel.json");
            generateFile(FMLPath, "guis/members.json", "guis/members.json");
            generateFile(FMLPath, "guis/groups.json", "guis/groups.json");
            generateFile(FMLPath, "guis/set_group.json", "guis/set_group.json");
            generateFile(FMLPath, "guis/permissions.json", "guis/permissions.json");
            generateFile(FMLPath, "guis/set_permission.json", "guis/set_permission.json");
            generateFile(FMLPath, "guis/members_group.json", "guis/members_group.json");
            generateFile(FMLPath, "guis/confirm_setspawn.json", "guis/confirm_setspawn.json");
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
            //throw new RuntimeException("Failed to create directory: " + dir, e);
        }
    }
}
