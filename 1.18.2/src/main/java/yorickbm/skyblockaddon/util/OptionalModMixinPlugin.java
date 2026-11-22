package yorickbm.skyblockaddon.util;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A mixin config for another mod: Mixin asks the plugin before it looks a target up, so a mixin the plugin skips logs
 * no missing target warning when the mod (or that version of it) is not installed.
 */
public abstract class OptionalModMixinPlugin implements IMixinConfigPlugin {

    protected static Optional<ArtifactVersion> installedVersion(final String modId) {
        final ModFileInfo modFile = LoadingModList.get().getModFileById(modId);
        if (modFile == null) return Optional.empty();
        return modFile.getMods().stream()
                .filter(mod -> modId.equals(mod.getModId()))
                .map(IModInfo::getVersion)
                .findFirst();
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
