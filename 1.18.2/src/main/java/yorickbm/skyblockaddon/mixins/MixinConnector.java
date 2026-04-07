package yorickbm.skyblockaddon.mixins;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class MixinConnector implements IMixinConnector {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void connect() {
        Mixins.addConfigurations("/mixins/skyblockaddon.mixin.json");
        Mixins.addConfiguration("/mixins/quark.mixin.json");
        Mixins.addConfiguration("/mixins/buildinggadgets.mixin.json");
        Mixins.addConfiguration("/mixins/the_vault.mixin.json");
        Mixins.addConfiguration("/mixins/buildscape.mixin.json");
    }
}