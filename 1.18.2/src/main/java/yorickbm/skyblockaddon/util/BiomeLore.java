package yorickbm.skyblockaddon.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class BiomeLore {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final int MAX_LINE_CHARS = 60;

    private static final int PROVIDER_SAMPLE_COUNT = 120;

    private static final ConcurrentHashMap<String, List<TextComponent>> CACHE = new ConcurrentHashMap<>();

    private static boolean bonemealWalkWarned = false;

    private BiomeLore() {}

    public static List<TextComponent> build(final String biomeId, final ServerLevel level) {
        return CACHE.computeIfAbsent(biomeId, id -> buildUncached(id, level));
    }

    private static List<TextComponent> buildUncached(final String biomeId, final ServerLevel level) {
        final Biome biome = level.registryAccess()
                .registryOrThrow(Registry.BIOME_REGISTRY)
                .get(new ResourceLocation(biomeId));
        if (biome == null) return Collections.emptyList();

        final List<TextComponent> lines = new ArrayList<>();
        appendSpawnLines(biome, lines);
        appendBonemealLines(biome, lines);
        return Collections.unmodifiableList(lines);
    }

    private static void appendSpawnLines(final Biome biome, final List<TextComponent> lines) {
        final MobSpawnSettings settings = biome.getMobSettings();

        final List<String> hostile = collectNames(settings, MobCategory.MONSTER);
        final List<String> passive = collectNames(settings, MobCategory.CREATURE);
        final List<String> water = new ArrayList<>();
        addUnique(water, collectNames(settings, MobCategory.WATER_CREATURE));
        addUnique(water, collectNames(settings, MobCategory.WATER_AMBIENT));
        addUnique(water, collectNames(settings, MobCategory.UNDERGROUND_WATER_CREATURE));
        addUnique(water, collectNames(settings, MobCategory.AXOLOTLS));
        final List<String> ambient = collectNames(settings, MobCategory.AMBIENT);

        final boolean anySpawns = !hostile.isEmpty() || !passive.isEmpty() || !water.isEmpty() || !ambient.isEmpty();
        if (!anySpawns) {
            lines.add((TextComponent) new TextComponent("Spawns: ").withStyle(ChatFormatting.GRAY)
                    .append(new TextComponent("none").withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }

        appendWrappedCategory(lines, "Hostile: ", ChatFormatting.RED, hostile);
        appendWrappedCategory(lines, "Passive: ", ChatFormatting.GREEN, passive);
        appendWrappedCategory(lines, "Water: ", ChatFormatting.AQUA, water);
        appendWrappedCategory(lines, "Ambient: ", ChatFormatting.YELLOW, ambient);
    }

    private static List<String> collectNames(final MobSpawnSettings settings, final MobCategory category) {
        return settings.getMobs(category).unwrap().stream()
                .map(spawner -> spawner.type)
                .distinct()
                .map(type -> type.getDescription().getString().toLowerCase())
                .collect(Collectors.toList());
    }

    private static void addUnique(final List<String> target, final List<String> toAdd) {
        for (final String name : toAdd) {
            if (!target.contains(name)) target.add(name);
        }
    }

    private static void appendWrappedCategory(final List<TextComponent> lines, final String label, final ChatFormatting labelColor, final List<String> names) {
        if (names.isEmpty()) return;

        final List<String> chunks = wrapAtWidth(names, MAX_LINE_CHARS - label.length());
        for (int i = 0; i < chunks.size(); i++) {
            final String prefix = i == 0 ? label : "  ";
            final ChatFormatting prefixColor = i == 0 ? labelColor : ChatFormatting.GRAY;
            lines.add((TextComponent) new TextComponent(prefix).withStyle(prefixColor)
                    .append(new TextComponent(chunks.get(i)).withStyle(ChatFormatting.GRAY)));
        }
    }

    private static List<String> wrapAtWidth(final List<String> names, final int firstLineBudget) {
        final List<String> chunks = new ArrayList<>();
        final int continuationBudget = MAX_LINE_CHARS - 2;
        StringBuilder current = new StringBuilder();
        int budget = Math.max(20, firstLineBudget);

        for (final String name : names) {
            final String toAdd = current.length() == 0 ? name : ", " + name;
            if (current.length() > 0 && current.length() + toAdd.length() > budget) {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(name);
                budget = continuationBudget;
            } else {
                current.append(toAdd);
            }
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    private static void appendBonemealLines(final Biome biome, final List<TextComponent> lines) {
        final Set<String> blockNames = new LinkedHashSet<>();
        addBlockName(blockNames, Blocks.GRASS);
        addBlockName(blockNames, Blocks.TALL_GRASS);

        try {
            final List<ConfiguredFeature<?, ?>> flowerFeatures = biome.getGenerationSettings().getFlowerFeatures();
            if (!flowerFeatures.isEmpty()) {
                walkFeature(flowerFeatures.get(0), blockNames);
            }
        } catch (final Throwable t) {
            if (!bonemealWalkWarned) {
                LOGGER.warn("Failed to extract bonemeal flower feature for biome lore (further errors suppressed)", t);
                bonemealWalkWarned = true;
            }
        }

        appendWrappedCategory(lines, "Bonemeal: ", ChatFormatting.LIGHT_PURPLE, new ArrayList<>(blockNames));
    }

    private static void walkFeature(final ConfiguredFeature<?, ?> feature, final Set<String> out) {
        final FeatureConfiguration config = feature.config();
        if (config instanceof RandomPatchConfiguration patch) {
            walkFeature(patch.feature().value().feature().value(), out);
        } else if (config instanceof SimpleBlockConfiguration simple) {
            sampleProvider(simple.toPlace(), out);
        }
    }

    private static void sampleProvider(final BlockStateProvider provider, final Set<String> out) {
        final Set<Block> blocks = new LinkedHashSet<>();
        final Random random = new Random();
        for (int i = 0; i < PROVIDER_SAMPLE_COUNT; i++) {
            random.setSeed((long) i * 104729L + 7L);
            final BlockPos pos = new BlockPos((i * 37) - 500, 64, (i * 53) - 500);
            try {
                final BlockState state = provider.getState(random, pos);
                if (state != null) blocks.add(state.getBlock());
            } catch (final Throwable t) {
                break;
            }
        }
        for (final Block block : blocks) addBlockName(out, block);
    }

    private static void addBlockName(final Set<String> out, final Block block) {
        final ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(block);
        if (rl != null) out.add(rl.getPath().replace('_', ' '));
    }
}
