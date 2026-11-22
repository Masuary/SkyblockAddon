package yorickbm.skyblockaddon.util;

import com.mojang.logging.LogUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Buildscape moved its pillar blocks between major versions: Skyvault ships 4.x, Wold's Vaults 3.x. Each line has its
 * own pillar mixin, and only the one matching the installed major version is applied.
 */
public class BuildscapeMixinPlugin extends OptionalModMixinPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BUILDSCAPE_MOD_ID = "buildscape";
    private static final String BUILDSCAPE_3_MIXIN_SIMPLE_NAME = "Buildscape3PillarMixinConfig";
    private static final int FIRST_MAJOR_VERSION_WITH_FEATURE_PACKAGES = 4;
    private static final int OLDEST_SUPPORTED_MAJOR_VERSION = 3;

    private int installedMajorVersion;

    @Override
    public void onLoad(final String mixinPackage) {
        final Optional<ArtifactVersion> version = installedVersion(BUILDSCAPE_MOD_ID);
        installedMajorVersion = version.map(ArtifactVersion::getMajorVersion).orElse(0);
        if (version.isPresent() && installedMajorVersion < OLDEST_SUPPORTED_MAJOR_VERSION) {
            LOGGER.warn("Buildscape {} is older than 3.x: its pillars are not protected by SkyblockAddon", version.get());
        }
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        final boolean isBuildscape3Mixin = mixinClassName.endsWith("." + BUILDSCAPE_3_MIXIN_SIMPLE_NAME);
        return isBuildscape3Mixin
                ? installedMajorVersion == OLDEST_SUPPORTED_MAJOR_VERSION
                : installedMajorVersion >= FIRST_MAJOR_VERSION_WITH_FEATURE_PACKAGES;
    }
}
