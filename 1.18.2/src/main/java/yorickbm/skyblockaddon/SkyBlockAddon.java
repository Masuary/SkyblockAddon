package yorickbm.skyblockaddon;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
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
import yorickbm.skyblockaddon.configs.SkyblockAddonConfig;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.core.util.RegistrySelector;
import yorickbm.skyblockaddon.core.util.ResourceManager;
import yorickbm.skyblockaddon.core.util.ThreadManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.events.*;
import yorickbm.skyblockaddon.events.Gui.GuiEvents;
import yorickbm.skyblockaddon.events.Gui.IslandGuiEvents;
import yorickbm.skyblockaddon.events.Gui.RegistryGuiEvents;

import java.io.File;
import java.io.IOException;
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

        //Register Forge minecraft events
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ModEvents());
        MinecraftForge.EVENT_BUS.register(new PlayerEvents());
        MinecraftForge.EVENT_BUS.register(new PermissionEvents());

        MinecraftForge.EVENT_BUS.register(new DefaultEventHandler());
        MinecraftForge.EVENT_BUS.register(new GuiEvents());
        MinecraftForge.EVENT_BUS.register(new IslandGuiEvents());
        MinecraftForge.EVENT_BUS.register(new RegistryGuiEvents());

        MinecraftForge.EVENT_BUS.register(new ChunkEvents());
        MinecraftForge.EVENT_BUS.register(new yorickbm.skyblockaddon.enhanced.EnhancedGuiListener());
        MinecraftForge.EVENT_BUS.register(new PneumaticCraftProtectionEvents());

        if(SkyblockAddonConfig.getForKey("island.particles.border").equalsIgnoreCase("TRUE")) MinecraftForge.EVENT_BUS.register(new ParticleEvents());
    }

    public static void CustomDebugMessages(final org.apache.logging.log4j.Logger log, final String msg) {
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

        //Register permissions
        PermissionManager.getInstance().loadPermissions(FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID + "/registries/PermissionRegistry.json"));

        //Init Version Checker
        VersionChecker.startVersionCheck();
    }

    /**
     * Runs upon server starting event.
     */
    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {

        //Loading public island owner names into username cache form Mojang API
        IslandManager.getInstance().getIslands().stream().filter(Island::isVisible).forEach(Island::updateName);

        LOGGER.info(String.format("Loaded %s guis.", GUILibraryRegistry.getGuis()));
        LOGGER.info(String.format("Loaded %s permissions.", PermissionManager.getInstance().getPermissions().size()));
        LOGGER.info("Vaulthunters Skyblock addon v{} has loaded!", SkyblockAddonCore.VERSION);
    }

    /**
     * Runs upon Shutdown Event of server.
     */
    @SubscribeEvent
    public void onServerShutDown(final ServerStoppedEvent event) {
        try {
            ChunkTaskScheduler.clear();
            ThreadManager.terminateAllThreads();
        } catch (final NoClassDefFoundError ex) {
            //Seems to be thrown
        }
    }

    private static CompoundTag IslandNBTData = null;
    public static CompoundTag getIslandNBT(final MinecraftServer server) {
        if (IslandNBTData == null) {
            try {
                final File islandFile = new File(FMLPaths.CONFIGDIR.get().resolve(SkyblockAddonCore.MOD_ID) + "/island.nbt");
                IslandNBTData = NbtIo.readCompressed(islandFile);
            } catch (final IOException e) {
                LOGGER.error("Could not load external island.nbt file, using mod's internal island.nbt file.");
                try {
                    final Resource rs = server.getResourceManager().getResource(new ResourceLocation(SkyblockAddonCore.MOD_ID, "structures/island.nbt"));
                    IslandNBTData = NbtIo.readCompressed(rs.getInputStream());
                } catch (final IOException ex) {
                    LOGGER.error("Could not load mod's internal island.nbt file!!!");
                }
            }
        }
        return IslandNBTData;
    }
}
