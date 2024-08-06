package yorickbm.skyblockaddon.events;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.command.ConfigCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.SkyblockAddon;
import yorickbm.skyblockaddon.capabilities.IslandGenerator;
import yorickbm.skyblockaddon.capabilities.PlayerIsland;
import yorickbm.skyblockaddon.capabilities.providers.IslandGeneratorProvider;
import yorickbm.skyblockaddon.capabilities.providers.PlayerIslandProvider;
import yorickbm.skyblockaddon.commands.*;
import yorickbm.skyblockaddon.commands.op.*;
import yorickbm.skyblockaddon.configs.SkyblockAddonLanguageConfig;
import yorickbm.skyblockaddon.islands.IslandData;
import yorickbm.skyblockaddon.util.ServerHelper;
import yorickbm.skyblockaddon.util.UsernameCache;

import java.awt.*;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SkyblockAddon.MOD_ID)
public class ModEvents {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        new IslandCommand(event.getDispatcher());
        new CreateIslandCommand(event.getDispatcher());
        new LeaveIslandCommand(event.getDispatcher());
        new InviteIslandCommand(event.getDispatcher());
        new AcceptIslandCommand(event.getDispatcher());
        new TeleportIslandCommand(event.getDispatcher());
        new JoinIslandCommand(event.getDispatcher());
        new IslandTravelCommand(event.getDispatcher());
        new SpawnCommand(event.getDispatcher());

        //Admin commands
        new GetIslandIdCommand(event.getDispatcher());
        new SetPlayersIslandCommand(event.getDispatcher());
        new SetIslandOwnerCommand(event.getDispatcher());
        new KickIslandMemberCommand(event.getDispatcher());
        new PromoteIslandMemberCommand(event.getDispatcher());
        new DemoteIslandMemberCommand(event.getDispatcher());
        new WhereAmICommand(event.getDispatcher());
        new GenerateIslandCommand(event.getDispatcher());
        new ToggleVisibilityCommand(event.getDispatcher());
        new SetIslandCenter(event.getDispatcher());

        ConfigCommand.register(event.getDispatcher());
        LOGGER.info("Registered commands for " + SkyblockAddon.MOD_ID);
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if(!(event.getObject() instanceof Player)) return;
        if(event.getObject().getCapability(PlayerIslandProvider.PLAYER_ISLAND).isPresent()) return;

        event.addCapability(new ResourceLocation(SkyblockAddon.MOD_ID, "islanddata"), new PlayerIslandProvider());
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesWorld(AttachCapabilitiesEvent<Level> event) {
        if(event.getObject().dimension() != Level.OVERWORLD) return;
        if(event.getObject().getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).isPresent()) return;

        if(event.getObject() instanceof ServerLevel level) {
            event.addCapability(new ResourceLocation(SkyblockAddon.MOD_ID, "properties"), new IslandGeneratorProvider(level.getServer()));
        }
    }

    @SubscribeEvent
    public static void onWorldSave(WorldEvent.Save event) {
        if(event.getWorld().isClientSide()) return;
        event.getWorld().getServer().getLevel(Level.OVERWORLD).getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(cap -> {
            cap.saveIslandsToFile(event.getWorld().getServer());
        });
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {

        event.getPlayer().getServer().getLevel(Level.OVERWORLD).getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(cap -> {
            if(cap.getIslands().size() == 0) {
                event.getPlayer().sendMessage(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("island.spawn.needed"), ChatFormatting.GREEN), event.getPlayer().getUUID());
            }
        });

        UsernameCache.onPlayerLogin(event.getPlayer());
        event.getPlayer().getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(i -> {
            ServerLevel overworld = event.getPlayer().getServer().getLevel(Level.OVERWORLD);
            if(overworld != null) {
                overworld.getCapability(IslandGeneratorProvider.ISLAND_GENERATOR).ifPresent(x -> {
                    UUID oldId = i.getIslandId();
                    if(oldId == null) return; //Got no island.

                    IslandData data = x.getIslandById(oldId);
                    if(data == null) {
                        i.setIsland(null); //Reset the island, since it does not exist!
                        LOGGER.info(event.getPlayer().getGameProfile().getName() +" joined with being part of island does not exist anymore. (" + oldId + ")");

                        event.getPlayer().teleportTo(overworld.getSharedSpawnPos().getX(), overworld.getSharedSpawnPos().getY(), overworld.getSharedSpawnPos().getZ());
                    }

                    if(data != null && !(data.hasMember(event.getPlayer().getUUID()) || data.isOwner(event.getPlayer().getUUID()))) {
                        i.setIsland(null); //Reset island id, since player is no longer part of it.
                        LOGGER.info(event.getPlayer().getGameProfile().getName() +" got kicked of their island while being offline. (\" + oldId + \")");

                        event.getPlayer().teleportTo(overworld.getSharedSpawnPos().getX(), overworld.getSharedSpawnPos().getY(), overworld.getSharedSpawnPos().getZ());
                        event.getPlayer().sendMessage(ServerHelper.formattedText(SkyblockAddonLanguageConfig.getForKey("island.member.kick")), event.getPlayer().getUUID());
                    }
                });
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if(!event.isWasDeath()) return;
        event.getOriginal().reviveCaps();

        event.getOriginal().getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(oldStore -> event.getPlayer().getCapability(PlayerIslandProvider.PLAYER_ISLAND).ifPresent(newStore -> newStore.copyFrom(oldStore)));

        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onDimensionChange (PlayerEvent.PlayerChangedDimensionEvent event) {
        event.getPlayer().reviveCaps();
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        event.getPlayer().reviveCaps();
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerIsland.class);
        event.register(IslandGenerator.class);
    }

}
