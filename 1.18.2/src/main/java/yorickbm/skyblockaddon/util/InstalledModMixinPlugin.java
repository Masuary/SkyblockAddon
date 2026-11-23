package yorickbm.skyblockaddon.util;

/**
 * Applies every mixin of its config only when one mod is installed, so a pack without that mod loads without a missing
 * mixin target warning.
 */
public abstract class InstalledModMixinPlugin extends OptionalModMixinPlugin {
    private final String modId;
    private boolean targetModLoaded;

    protected InstalledModMixinPlugin(final String modId) {
        this.modId = modId;
    }

    @Override
    public void onLoad(final String mixinPackage) {
        targetModLoaded = installedModFile(modId).isPresent();
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return targetModLoaded;
    }
}
