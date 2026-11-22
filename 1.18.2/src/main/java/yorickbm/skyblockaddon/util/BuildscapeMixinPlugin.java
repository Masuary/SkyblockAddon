package yorickbm.skyblockaddon.util;

import net.minecraftforge.fml.loading.moddiscovery.ModFile;

import java.nio.file.Files;
import java.util.Optional;

/**
 * Buildscape moved its pillar blocks from the block package (up to 4.0.x; Wold's Vaults ships 3.0.9) to
 * feature.pillar (4.1.x; Skyvault). Each location has its own pillar mixin, and a target is applied only when the
 * installed Buildscape jar contains that class.
 */
public class BuildscapeMixinPlugin extends OptionalModMixinPlugin {
    private static final String BUILDSCAPE_MOD_ID = "buildscape";

    private Optional<ModFile> buildscapeFile = Optional.empty();

    @Override
    public void onLoad(final String mixinPackage) {
        buildscapeFile = installedModFile(BUILDSCAPE_MOD_ID);
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        final String classResource = targetClassName.replace('.', '/') + ".class";
        return buildscapeFile.map(modFile -> Files.exists(modFile.findResource(classResource))).orElse(false);
    }
}
