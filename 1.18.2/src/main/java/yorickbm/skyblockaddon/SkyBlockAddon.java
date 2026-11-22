package yorickbm.skyblockaddon;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.MinecraftForge;
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
import yorickbm.skyblockaddon.chunk.ChunkTaskScheduler;
import yorickbm.skyblockaddon.commands.IslandBanCommand;
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.util.RegistrySelector;
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
import java.util.Map;
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
        //Register Resources
        RegistrySelector selector = new RegistrySelector(Map.of(
                "PermissionRegistry", () -> {
                    if (ModList.get().isLoaded("the_vault")) {
                        return "registries/permissions/vaulthunters.json";
                    } else {
                        return "registries/permissions/default.json";
                    }
                }
        ));
        ResourceManager.commonSetup(FMLPaths.CONFIGDIR.get(), selector);

        //Register username cache
        UsernameCache.initCache(500);

        //Register guis
        GUILibraryRegistry.registerFolder(SkyblockAddonCore.MOD_ID, FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID + "/guis/"));

        loadIslandPermissions();
        loadIslandTemplate();

        //Init Version Checker
        VersionChecker.startVersionCheck();
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

    private static volatile CompoundTag islandTemplate;

    /**
     * Read the server's island.nbt (generated from the bundled island when missing) once at startup. An unreadable,
     * corrupt or empty file stops the server instead of failing every island creation later.
     */
    public static void loadIslandTemplate() {
        final Path templatePath = FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID + "/island.nbt");
        final CompoundTag template = readIslandTemplate(templatePath);
        final int blockCount = template.getList("blocks", Tag.TAG_COMPOUND).size();
        if (blockCount == 0 || template.getList("palette", Tag.TAG_COMPOUND).isEmpty()) {
            throw new IllegalStateException("'" + templatePath.toAbsolutePath() + "' is not a structure file with blocks and a palette; refusing to create islands from it");
        }
        islandTemplate = template;
        LOGGER.info("Loaded the island template with {} blocks from '{}'.", blockCount, templatePath);
    }

    private static CompoundTag readIslandTemplate(final Path templatePath) {
        try {
            return NbtIo.readCompressed(templatePath.toFile());
        } catch (final IOException | RuntimeException readFailure) {
            throw new IllegalStateException("Failed to load the island template from '" + templatePath.toAbsolutePath() + "': " + readFailure, readFailure);
        }
    }

    public static CompoundTag getIslandNBT() {
        final CompoundTag template = islandTemplate;
        if (template == null) throw new IllegalStateException("The island template was not loaded at startup");
        return template;
    }
}
