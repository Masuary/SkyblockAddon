package yorickbm.skyblockaddon.util;

/**
 * Applies the Effortless Building mixins only when the mod is installed, so a pack without it (Skyvault) loads without
 * a missing mixin target warning.
 */
public class EffortlessBuildingMixinPlugin extends OptionalModMixinPlugin {
    private static final String EFFORTLESS_BUILDING_MOD_ID = "effortlessbuilding";

    private boolean targetModLoaded;

    @Override
    public void onLoad(final String mixinPackage) {
        targetModLoaded = installedVersion(EFFORTLESS_BUILDING_MOD_ID).isPresent();
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return targetModLoaded;
    }
}
