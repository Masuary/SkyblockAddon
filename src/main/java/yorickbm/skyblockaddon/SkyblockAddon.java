package yorickbm.skyblockaddon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.events.BlockEvents;
import yorickbm.skyblockaddon.events.ModEvents;
import yorickbm.skyblockaddon.events.ParticleEvents;
import yorickbm.skyblockaddon.events.PlayerEvents;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.TerralithFoundException;
import yorickbm.skyblockaddon.util.ThreadManager;
import yorickbm.skyblockaddon.util.UsernameCache;
import yorickbm.skyblockaddon.util.modintegration.ModIntegrationHandler;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(SkyblockAddon.MOD_ID)
public class SkyblockAddon {

    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "skyblockaddon";
    public static final String VERSION = "7.0";

    public static final float UI_SOUND_VOL = 0.5f;
    public static final float EFFECT_SOUND_VOL = 0.2f;

    private static CompoundTag IslandNBTData = null;

    public SkyblockAddon() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::processIMC);

        //Register username cache
        UsernameCache.initCache(120);

        //Register configs
        FileUtils.getOrCreateDirectory(FMLPaths.CONFIGDIR.get().resolve(MOD_ID), MOD_ID);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SkyblockAddonLanguageConfig.SPEC, MOD_ID + "/language.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SkyblockAddonConfig.SPEC, MOD_ID + "/config.toml");

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ModEvents());
        MinecraftForge.EVENT_BUS.register(new BlockEvents());
        MinecraftForge.EVENT_BUS.register(new PlayerEvents());

        if(SkyblockAddonConfig.getForKey("island.particles.border").equalsIgnoreCase("TRUE")) {
            MinecraftForge.EVENT_BUS.register(new ParticleEvents());
        }

    }

    /**
     * Load custom .NBT file for island structure
     * @param server
     * @return NBT data
     */
    public static CompoundTag getIslandNBT(MinecraftServer server) {
        if(IslandNBTData == null) {
            try {
                File islandFile = new File(FMLPaths.CONFIGDIR.get().resolve(SkyblockAddon.MOD_ID) + "/island.nbt");
                IslandNBTData = NbtIo.readCompressed(islandFile);
            } catch (IOException e) {
                LOGGER.error("Could not load external island.nbt file, using mod's internal island.nbt file.");
                try {
                    Resource rs = server.getResourceManager().getResource(new ResourceLocation(SkyblockAddon.MOD_ID, "structures/island.nbt"));
                    IslandNBTData = NbtIo.readCompressed(rs.getInputStream());
                } catch (IOException ex) {
                    LOGGER.error("Could not load mod's internal island.nbt file!!!");
                }
            }
        }
        return IslandNBTData;
    }

    private void processIMC(final InterModProcessEvent event) {
        // some example code to receive and process InterModComms from other mods
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m -> m.messageSupplier().get()).
                collect(Collectors.toList()));

        // Determine if Terralith is found
        if(ModList.get().isLoaded("terralith")) {
            LOGGER.error("Beware, skyblockaddon mod is loaded together with Terralith!");
            throw new TerralithFoundException();
        }

        // Setup Integration Handler for mods
        ModIntegrationHandler.setup();
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

        //Custom island.nbt
        try {
            Resource rs = event.getServer().getResourceManager().getResource(new ResourceLocation(SkyblockAddon.MOD_ID, "structures/island.nbt"));
            CompoundTag nbt = NbtIo.readCompressed(rs.getInputStream());
            File islandFile = new File(FMLPaths.CONFIGDIR.get().resolve(MOD_ID) + "/island.nbt");
            if(!islandFile.exists()) {
                if(islandFile.createNewFile()) { // Make sure we could create the new file
                    NbtIo.writeCompressed(nbt, islandFile);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Check mod version
        Optional<? extends ModContainer> modContainer = ModList.get().getModContainerById(MOD_ID);
        if(modContainer.isPresent()) {
            VersionChecker.CheckResult result = VersionChecker.getResult(modContainer.get().getModInfo());
            LOGGER.info("Vaulthunters Skyblock addon v" + VERSION + " (" + result.status().name() + ") has loaded!");
        }
    }

    @SubscribeEvent
    public void onServerShutDown(ServerStoppedEvent event) {
        try {
            ThreadManager.terminateAllThreads();
        } catch(NoClassDefFoundError ex) {
            //Some reason this gets thrown from time to time
        }
    }

    public static IslandData CheckOnIsland(Entity player) {
        AtomicReference<IslandData> island = new AtomicReference<>(null);

        player.getLevel().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(islandGenerator -> {

            UUID islandIdOn = islandGenerator.getIslandIdByLocation(new Vec3i(player.getX(), 121, player.getZ()));
            if(islandIdOn == null) return; //Not on an island so we do not affect permission

            IslandData data = islandGenerator.getIslandById(islandIdOn);
            island.set(data);
        });

        return island.get();
    }

    public static IslandData GetIslandByBlockPos(BlockPos location, Entity player) {
        AtomicReference<IslandData> island = new AtomicReference<>(null);

        Objects.requireNonNull(Objects.requireNonNull(player.getServer()).getLevel(Level.OVERWORLD)).getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(islandGenerator -> {
            UUID islandIdOn = islandGenerator.getIslandIdByLocation(new Vec3i(location.getX(), 121, location.getZ()));
            if(islandIdOn == null ) return; //Not on an island so we do not affect permission

            IslandData data = islandGenerator.getIslandById(islandIdOn);
            island.set(data);
        });

        return island.get();
    }

}
