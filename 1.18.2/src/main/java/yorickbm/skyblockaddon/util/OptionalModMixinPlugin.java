package yorickbm.skyblockaddon.util;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A mixin config for another mod: Mixin asks the plugin before it looks a target up, so a mixin the plugin skips logs
 * no missing target warning when the mod, or the target class in it, is not installed.
 */
public abstract class OptionalModMixinPlugin implements IMixinConfigPlugin {

    protected static Optional<ModFile> installedModFile(final String modId) {
        return Optional.ofNullable(LoadingModList.get().getModFileById(modId)).map(ModFileInfo::getFile);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
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
