package yorickbm.skyblockaddon.events;

import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.command.ConfigCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldCapability;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.commands.*;
import yorickbm.skyblockaddon.commands.op.*;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;

public class ModEvents {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public void onCommandRegister(final RegisterCommandsEvent event) {
        //Basic commands
        new IslandCommand(event.getDispatcher());
        new IslandCreateCommand(event.getDispatcher());
        new IslandInviteCommand(event.getDispatcher());
        new IslandLeaveCommand(event.getDispatcher());
        new IslandRequestTeleportCommand(event.getDispatcher());
        new IslandTeleportCommand(event.getDispatcher());
        new IslandTravelCommand(event.getDispatcher());
        new IslandHubCommand(event.getDispatcher());
        new IslandAddGroupMemberCommand(event.getDispatcher());

        //Functional commands
        new FunctionRegistryCommand(event.getDispatcher());

        //Admin commands
        new AdminGetIdCommand(event.getDispatcher());
        new AdminMenuCommand(event.getDispatcher());
        new AdminTeleportCommand(event.getDispatcher());
        new ConfigReloadCommand(event.getDispatcher());
        new AdminAddMemberCommand(event.getDispatcher());
        new DebugCommand(event.getDispatcher());
        new AdminPurgeCommand(event.getDispatcher());
        new AdminSkullCommand(event.getDispatcher());
        new AdminLoadIslandCommand(event.getDispatcher());
        new AdminPermissionCommand(event.getDispatcher());
        new AdminCleanChunksCommand(event.getDispatcher());
        new AdminAbandonedIslandsCommand(event.getDispatcher());
        new AdminReservedSlotsCommand(event.getDispatcher());

        ConfigCommand.register(event.getDispatcher());
        LOGGER.info("Registered commands for " + SkyblockAddonCore.MOD_ID);
    }

    /**
     * The overworld fires this right after it is constructed (its capabilities.dat already read) and before spawn
     * chunks load or players join. A failure stops the server: running without the islands switches protection off.
     */
    @SubscribeEvent
    public void onWorldLoad(final WorldEvent.Load event) {
        if (!(event.getWorld() instanceof final ServerLevel level) || level.dimension() != Level.OVERWORLD) return;

        final SkyblockAddonWorldCapability capability = level.getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).resolve()
                .orElseThrow(() -> new IllegalStateException("SkyblockAddon world capability is missing on the overworld; cannot load islands"));
        try {
            capability.loadIslandsFromDisk();
        } catch (final RuntimeException loadFailure) {
            throw new IllegalStateException("SkyblockAddon could not load the islands; stopping the server so island protection is never off: " + loadFailure.getMessage(), loadFailure);
        }
    }

    @SubscribeEvent
    public void onWorldSave(final WorldEvent.Save event) {
        if (event.getWorld().isClientSide()) return;
        if (!(event.getWorld() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return; //World save fires once per dimension; only act on the overworld.
        level.getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(SkyblockAddonWorldCapability::saveIslandsToDisk);
    }

    @SubscribeEvent
    public void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
        event.register(SkyblockAddonWorldCapability.class);
    }

    @SubscribeEvent
    public void onAttachCapabilitiesWorld(final AttachCapabilitiesEvent<Level> event) {
        if (event.getObject().dimension() != Level.OVERWORLD) return;
        if (event.getObject().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).isPresent())
            return;

        if (event.getObject() instanceof final ServerLevel level) {
            event.addCapability(ResourceLocation.fromNamespaceAndPath(SkyblockAddonCore.MOD_ID, "properties"), new SkyblockAddonWorldProvider(event.getObject().getServer()));
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(final PlayerEvent.PlayerLoggedInEvent event) {
        final GameProfile profile = event.getPlayer().getGameProfile();
        UsernameCache.registerPlayername(profile.getId(), profile.getName());

        event.getPlayer().getLevel().getServer().getLevel(Level.OVERWORLD).getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
            //Load user island into cache
            IslandManager.getInstance().loadIslandIntoReverseCache(event.getPlayer().getUUID());
        });
    }
}
