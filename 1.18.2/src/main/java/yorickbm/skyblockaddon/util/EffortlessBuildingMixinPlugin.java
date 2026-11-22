package yorickbm.skyblockaddon.util;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Effortless Building mixins only when the mod is installed, so a pack without it (Skyvault) loads without
 * a missing mixin target warning.
 */
public class EffortlessBuildingMixinPlugin implements IMixinConfigPlugin {
    private static final String EFFORTLESS_BUILDING_MOD_ID = "effortlessbuilding";

    private boolean targetModLoaded;

    @Override
    public void onLoad(final String mixinPackage) {
        targetModLoaded = LoadingModList.get().getModFileById(EFFORTLESS_BUILDING_MOD_ID) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return targetModLoaded;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }
}
