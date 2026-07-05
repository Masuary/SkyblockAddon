package yorickbm.skyblockaddon.mixins;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

import java.util.List;
import java.util.function.Predicate;

public class MixinConnector implements IMixinConnector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<OptionalMixinConfiguration> OPTIONAL_CONFIGURATIONS = List.of(
            new OptionalMixinConfiguration(
                    "/mixins/quark.mixin.json",
                    "vazkii.quark.content.tweaks.module.ReacharoundPlacingModule"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/buildinggadgets.mixin.json",
                    "com.direwolf20.buildinggadgets.common.items.GadgetBuilding"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/the_vault.mixin.json",
                    "iskallia.vault.block.VaultPortalBlock"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/buildscape.mixin.json",
                    "com.kingodogo.buildscape.block.PillarBlock"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/easy_villagers.mixin.json",
                    "de.maxhenkel.easyvillagers.events.BlockEvents"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/easy_piglins.mixin.json",
                    "de.maxhenkel.easypiglins.events.PiglinEvents"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/elevatorid.mixin.json",
                    "xyz.vsngamer.elevatorid.network.TeleportHandler"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/effortlessbuilding.mixin.json",
                    "nl.requios.effortlessbuilding.helper.SurvivalHelper"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/pneumaticcraft.mixin.json",
                    "me.desht.pneumaticcraft.common.ai.DroneEntityAIPickupItems"
            ),
            new OptionalMixinConfiguration(
                    "/mixins/ars_nouveau.mixin.json",
                    "com.hollingsworth.arsnouveau.common.block.tile.PortalTile"
            )
    );

    @Override
    public void connect() {
        Mixins.addConfiguration("/mixins/skyblockaddon.mixin.json");
        for (final OptionalMixinConfiguration optionalConfiguration : OPTIONAL_CONFIGURATIONS) {
            if (isClassResourcePresent(optionalConfiguration.targetClassName())) {
                Mixins.addConfiguration(optionalConfiguration.configuration());
            } else {
                LOGGER.debug(
                        "Skipping optional mixin configuration {} because {} is absent",
                        optionalConfiguration.configuration(),
                        optionalConfiguration.targetClassName()
                );
            }
        }
    }

    static boolean isClassResourcePresent(final String className) {
        final String resourceName = className.replace('.', '/') + ".class";
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null && contextClassLoader.getResource(resourceName) != null) return true;
        return MixinConnector.class.getClassLoader().getResource(resourceName) != null;
    }

    static List<String> availableOptionalConfigurations(final Predicate<String> targetClassPresent) {
        return OPTIONAL_CONFIGURATIONS.stream()
                .filter(configuration -> targetClassPresent.test(configuration.targetClassName()))
                .map(OptionalMixinConfiguration::configuration)
                .toList();
    }

    private record OptionalMixinConfiguration(String configuration, String targetClassName) {}
}
