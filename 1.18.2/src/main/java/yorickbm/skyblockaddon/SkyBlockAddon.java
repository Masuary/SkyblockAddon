package yorickbm.skyblockaddon;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FileUtils;
import org.slf4j.Logger;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.guilibrary.events.DefaultEventHandler;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability.ParsedIslandStructure;
import yorickbm.skyblockaddon.chunk.ChunkTaskScheduler;
import yorickbm.skyblockaddon.commands.IslandBanCommand;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.util.BundledDefaultsPack;
import yorickbm.skyblockaddon.core.util.ResourceManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.events.ChunkEvents;
import yorickbm.skyblockaddon.events.IslandBanEvents;
import yorickbm.skyblockaddon.events.ModEvents;
import yorickbm.skyblockaddon.events.ParticleEvents;
import yorickbm.skyblockaddon.events.PermissionEvents;
import yorickbm.skyblockaddon.events.PlayerEvents;
import yorickbm.skyblockaddon.events.PneumaticCraftProtectionEvents;
import yorickbm.skyblockaddon.events.Gui.GuiEvents;
import yorickbm.skyblockaddon.events.Gui.IslandGuiEvents;
import yorickbm.skyblockaddon.events.Gui.RegistryGuiEvents;
import yorickbm.skyblockaddon.islands.IslandCreationQueue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("skyblockaddon")
public class SkyBlockAddon {
    private static final Logger LOGGER = LogUtils.getLogger();

    public SkyBlockAddon() {
        //Register Forge ModLoading events
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);

        //Register configs
        FileUtils.getOrCreateDirectory(FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID), SkyblockAddonCore.MOD_ID);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SkyblockAddonConfig.SPEC, SkyblockAddonCore.MOD_ID + "/config.toml");
        IslandManager.getInstance().setConfiguredReservedSlotsSource(SkyblockAddonConfig::getReservedSlots);

        //Register Forge minecraft events
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ModEvents());
        MinecraftForge.EVENT_BUS.register(new PlayerEvents());
        MinecraftForge.EVENT_BUS.register(new IslandBanEvents());
        PermissionEvents.register(MinecraftForge.EVENT_BUS);

        MinecraftForge.EVENT_BUS.register(new DefaultEventHandler());
        MinecraftForge.EVENT_BUS.register(new GuiEvents());
        MinecraftForge.EVENT_BUS.register(new IslandGuiEvents());
        MinecraftForge.EVENT_BUS.register(new RegistryGuiEvents());

        MinecraftForge.EVENT_BUS.register(new ChunkEvents());
        MinecraftForge.EVENT_BUS.register(new yorickbm.skyblockaddon.enhanced.EnhancedGuiListener());
        MinecraftForge.EVENT_BUS.register(new PneumaticCraftProtectionEvents());
        MinecraftForge.EVENT_BUS.register(IslandCreationQueue.getInstance());

        if(SkyblockAddonConfig.getForKey("island.particles.border").equalsIgnoreCase("TRUE")) MinecraftForge.EVENT_BUS.register(new ParticleEvents());
    }

    public static void logPermissionDebug(final org.apache.logging.log4j.Logger log, final String msg) {
        if(SkyblockAddonConfig.getForKey("permissions.debug").equalsIgnoreCase("TRUE")) log.info(msg);
    }

    private void processIMC(InterModProcessEvent event) {
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m -> m.messageSupplier().get()).
                collect(Collectors.toList()));
    }

    /**
     * Run Common config setup.
     */
    public void onCommonSetup(final FMLCommonSetupEvent event) {
        generateMissingConfigFiles();

        //Register username cache
        UsernameCache.initCache(500);

        //Register guis
        GUILibraryRegistry.registerFolder(SkyblockAddonCore.MOD_ID, FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID + "/guis/"));

        loadIslandPermissions();

        //Init Version Checker
        VersionChecker.startVersionCheck();
    }

    /**
     * Generate every missing config file from the bundled copy of the loaded pack (at startup and on /island admin
     * reload). A generated island.nbt is worth a warning: a server with a custom island would otherwise build the
     * bundled one unnoticed.
     */
    public static void generateMissingConfigFiles() {
        final List<String> generatedFiles = ResourceManager.commonSetup(FMLPaths.CONFIGDIR.get(), BundledDefaultsPack.select(ModList.get()::isLoaded));
        if (generatedFiles.contains(ResourceManager.ISLAND_TEMPLATE_FILE)) {
            LOGGER.warn("'{}' was missing and has been generated from the bundled island; new islands use the bundled island",
                    islandTemplatePath());
        }
    }

    /**
     * Load the server's permission registry and report each known trigger it leaves unprotected.
     */
    public static void loadIslandPermissions() {
        final Path registryPath = FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID + "/registries/PermissionRegistry.json");
        final int permissionCount = PermissionManager.getInstance().loadPermissions(registryPath);
        LOGGER.info("Loaded {} island permissions.", permissionCount);
        PermissionManager.getInstance().findUnreferencedTriggers().forEach(trigger ->
                LOGGER.error("No island permission in '{}' lists trigger '{}', so every action on it is allowed for everyone on every island", registryPath, trigger));
    }

    /**
     * The island template is loaded for each dedicated or integrated server start, not at mod setup, so a client with a
     * broken island.nbt still reaches the title screen. A throw here fails the server start: Forge's event bus rethrows
     * it and MinecraftServer.runServer logs it, writes a crash report and stops the server.
     */
    @SubscribeEvent
    public void onServerAboutToStart(final ServerAboutToStartEvent event) {
        loadIslandTemplate();
    }

    /**
     * Runs upon server starting event.
     */
    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {
        SkyblockAddonConfig.validateIslandCreationSettings();

        //Loading public island owner names into username cache form Mojang API
        IslandManager.getInstance().getIslands().stream().filter(Island::isVisible).forEach(Island::updateName);

        LOGGER.info(String.format("Loaded %s guis.", GUILibraryRegistry.getGuis()));
        LOGGER.info(String.format("Loaded %s permissions.", PermissionManager.getInstance().getPermissions().size()));
        LOGGER.info("Vaulthunters Skyblock addon v{} has loaded!", ModList.get().getModContainerById(SkyblockAddonCore.MOD_ID).orElseThrow().getModInfo().getVersion());
    }

    /**
     * Runs upon Shutdown Event of server.
     */
    @SubscribeEvent
    public void onServerShutDown(final ServerStoppedEvent event) {
        IslandBanCommand.clearPendingLookups();
        try {
            ChunkTaskScheduler.clear();
        } catch (final NoClassDefFoundError ex) {
            //Seems to be thrown
        }
    }

    private static volatile ParsedIslandStructure islandStructure;

    /**
     * Read and parse the server's island.nbt (generated from the bundled island when missing). An unreadable, corrupt
     * or structurally broken file stops the server start instead of failing every island creation later.
     */
    public static void loadIslandTemplate() {
        final Path templatePath = islandTemplatePath();
        final ParsedIslandStructure structure = parseIslandTemplate(templatePath, readIslandTemplate(templatePath));
        islandStructure = structure;
        LOGGER.info("Loaded the island template with {} blocks from '{}' in {} ms.", structure.blocks.size(), templatePath, structure.parseMillis);
    }

    private static ParsedIslandStructure parseIslandTemplate(final Path templatePath, final CompoundTag template) {
        try {
            return ParsedIslandStructure.parse(template);
        } catch (final RuntimeException parseFailure) {
            throw new IllegalStateException("'" + templatePath.toAbsolutePath() + "' is not a usable island structure: " + parseFailure.getMessage(), parseFailure);
        }
    }

    private static Path islandTemplatePath() {
        return FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID).resolve(ResourceManager.ISLAND_TEMPLATE_FILE);
    }

    private static CompoundTag readIslandTemplate(final Path templatePath) {
        try {
            return NbtIo.readCompressed(templatePath.toFile());
        } catch (final IOException | RuntimeException readFailure) {
            throw new IllegalStateException("Failed to load the island template from '" + templatePath.toAbsolutePath() + "': " + readFailure, readFailure);
        }
    }

    public static ParsedIslandStructure getIslandStructure() {
        final ParsedIslandStructure structure = islandStructure;
        if (structure == null) throw new IllegalStateException("The island template was not loaded when the server started");
        return structure;
    }
}
