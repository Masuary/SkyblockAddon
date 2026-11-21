package yorickbm.skyblockaddon.events.Gui;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.events.IslandEvents;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.FunctionRegistry;
import yorickbm.skyblockaddon.util.IslandGroupAssignments;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public class IslandGuiEvents {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public void onTeleportToIslandEvent(final IslandEvents.TeleportToIsland event) {
        if(event.isCanceled()) return; //Skip if canceled

        //Teleport target to island
        event.getIsland().teleportTo(event.getTarget());
        event.getHolder().close();

        //Send message of success
        event.getTarget().sendMessage(new TextComponent(
                SkyBlockAddonLanguage.getLocalizedString("island.travel")
        ).withStyle(ChatFormatting.GREEN),event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onLeaveIslandEvent(final IslandEvents.LeaveIsland event) {
        //Run leave command
        Objects.requireNonNull(event.getTarget().getServer()).getCommands().performCommand(event.getTarget().createCommandSourceStack(), "/island leave");
    }

    @SubscribeEvent
    public void onSetSpawnPointEvent(final IslandEvents.SetSpawnPoint event) {
        if(event.isCanceled()) return; //Skip if canceled

        if(!event.getIsland().isOwner(event.getTarget().getUUID())
                && !event.getTarget().hasPermissions(Commands.LEVEL_ADMINS)) {
            event.getTarget().sendMessage(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("island.setspawn.not.allowed")
            ).withStyle(ChatFormatting.RED),event.getTarget().getUUID());

            event.setResult(Event.Result.DENY);
            return;
        }

        if(!event.getIsland().getIslandBoundingBox().isInside(ForgeConverter.ForgeToInternalVec3i(event.getTarget().getOnPos()))) {
            event.getTarget().sendMessage(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("island.setspawn.outside")
            ).withStyle(ChatFormatting.RED),event.getTarget().getUUID());

            event.setResult(Event.Result.DENY);
            return;
        }

        event.getIsland().setSpawnPoint(ForgeConverter.ForgeToInternalVec3i(event.getTarget().blockPosition()));
        event.getHolder().update();
    }

    @SubscribeEvent
    public void onChangeIslandVisiblityEvent(final IslandEvents.ChangeVisibility event) {
        event.getIsland().toggleVisibility();
        event.getHolder().update();
    }

    @SubscribeEvent
    public void onTravelToIslandEvent(final IslandEvents.TravelToIsland event) {
        final CompoundTag modData = event.getClickedItem().getOrCreateTagElement(SkyblockAddonCore.MOD_ID);

        if(!modData.contains("island_id")) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(modData.getUUID("island_id"));

        //Island is not found
        if(island == null) {
            event.setResult(Event.Result.DENY);
            return;
        }

        event.getHolder().close();

        island.teleportTo(event.getTarget());
        event.getTarget().sendMessage(new TextComponent(
                SkyBlockAddonLanguage.getLocalizedString("island.travel")
        ).withStyle(ChatFormatting.GREEN),event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onInviteNewMemberEvent(final IslandEvents.InviteNewMember event) {
        final CompoundTag guiData = event.getHolder().getData();

        LOGGER.info(guiData);

        if(!guiData.contains("group_id")) {
            event.setResult(Event.Result.DENY);
            return;
        }

        event.getHolder().close();
        event.getTarget().sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.click.to.add"))
                        .withStyle(ChatFormatting.GREEN)
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/island group \""
                                        +  event.getIsland().getGroup(guiData.getUUID("group_id")).getName()
                                        + "\" addMember "
                                        + event.getTarget().getName().getString().trim())))
                ,event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onCreateNewGroupEvent(final IslandEvents.CreateNewGroup event) {
        event.getHolder().close();

        //Create clickable command
        final UUID id = UUID.randomUUID();
        final Function<ServerPlayer, Boolean> function = executor -> {
            // Check if hand is empty or air
            if (executor.getMainHandItem().isEmpty() || executor.getMainHandItem().getItem() == Items.AIR) {
                executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.failure")
                                .formatted(executor.getMainHandItem().getDisplayName().getString(),
                                        Objects.requireNonNull(executor.getMainHandItem().getItem().getRegistryName()).toString().split(":")[1]))
                                .withStyle(ChatFormatting.RED),
                        executor.getUUID());
                return false;
            }

            // Check if group name is unique
            final String newGroupName = executor.getMainHandItem().getDisplayName().getString().trim();
            final boolean nameAlreadyExists = event.getIsland().getGroups().stream()
                    .anyMatch(g -> g.getName().equalsIgnoreCase(newGroupName));

            if (nameAlreadyExists) {
                executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.name.exists")
                                .formatted(newGroupName))
                                .withStyle(ChatFormatting.RED),
                        executor.getUUID());
                return false;
            }

            ServerHelper.playSongToPlayer(executor, SoundEvents.NOTE_BLOCK_CHIME, SkyblockAddonCore.UI_SUCCESS_VOL, 1f);
            event.getIsland().addGroup(new ForgeIslandGroup(UUID.randomUUID(), executor.getMainHandItem(), false));

            executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.created")
                            .formatted(newGroupName, Objects.requireNonNull(executor.getMainHandItem().getItem().getRegistryName()).toString().split(":")[1].trim()))
                            .withStyle(ChatFormatting.GREEN),
                    executor.getUUID());

            return true;
        };
        FunctionRegistry.registerFunction(id, function, 15);

        event.getTarget().sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.create"))
                        .withStyle(ChatFormatting.GREEN)
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, FunctionRegistry.getCommand(id))))
                ,event.getTarget().getUUID());

    }

    @SubscribeEvent
    public void onUpdateBiomeEvent(final IslandEvents.UpdateBiome event) {
        final CompoundTag modData = event.getClickedItem().getOrCreateTagElement(SkyblockAddonCore.MOD_ID);


        if(!modData.contains("biome")) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final String biome = modData.getString("biome");
        event.getIsland().updateBiome(biome, event.getTarget().getLevel());
        event.getHolder().close();

        event.getTarget().sendMessage(
                new TextComponent(
                        String.format(SkyBlockAddonLanguage.getLocalizedString("island.biome"), biome))
                        .withStyle(ChatFormatting.GREEN),
                event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onKickMemberEvent(final IslandEvents.KickMember event) {
        final CompoundTag guiData = event.getHolder().getData().getCompound(SkyblockAddonCore.MOD_ID);

        if(!guiData.contains("player_id")) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final UUID playerUUID = guiData.getUUID("player_id");
        event.getIsland().kickMember(event.getTarget(), playerUUID);
        event.getHolder().close();

        event.getTarget().sendMessage(
                new TextComponent(
                        SkyBlockAddonLanguage.getLocalizedString("island.member.kicked")
                                .formatted( UsernameCache.getBlocking(playerUUID) )
                ).withStyle(ChatFormatting.GREEN),
                event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onSetPlayerGroupEvent(final IslandEvents.SetPlayerGroup event) {
        final CompoundTag modData = event.getClickedItem().getOrCreateTag();
        final CompoundTag guiData = event.getHolder().getData().getCompound(SkyblockAddonCore.MOD_ID);

        if(!guiData.contains("player_id") || !modData.contains("group_id")) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final UUID groupUUID = modData.getUUID("group_id");
        final UUID playerUUID = guiData.getUUID("player_id");
        final var targetGroup = event.getIsland().getGroup(groupUUID);

        final boolean assigned = event.getIsland().isPartOf(playerUUID)
                ? event.getIsland().addMember(playerUUID, groupUUID)
                : IslandGroupAssignments.assignWithoutMembership(event.getIsland(), playerUUID, targetGroup);
        if(!assigned) {
            event.setResult(Event.Result.DENY);
            return;
        }
        event.getHolder().close();

        event.getTarget().sendMessage(
                new TextComponent(
                        SkyBlockAddonLanguage.getLocalizedString("island.member.group.set")
                                .formatted(
                                        UsernameCache.getBlocking(playerUUID),
                                        targetGroup.getName()
                                )
                ).withStyle(ChatFormatting.GREEN),
                event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onRemoveGroup(final IslandEvents.RemoveGroup event) {
        final CompoundTag guiData = event.getHolder().getData();
        if(!guiData.contains("group_id")) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final UUID groupUUID = guiData.getUUID("group_id");
        final ForgeIslandGroup group = (ForgeIslandGroup) event.getIsland().getGroup(groupUUID);
        event.getHolder().close();

        if(event.getIsland().removeGroup(groupUUID)) {
            event.getTarget().sendMessage(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("island.group.remove.success"),
                            group.getItem().getDisplayName().getString().trim()))
                            .withStyle(ChatFormatting.RED)
                    ,event.getTarget().getUUID());
            event.setResult(Event.Result.ALLOW);
            return;
        }

        event.getTarget().sendMessage(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("island.group.remove.failed"),
                        group.getItem().getDisplayName().getString().trim()))
                        .withStyle(ChatFormatting.RED)
                ,event.getTarget().getUUID());
        event.setResult(Event.Result.DENY);

    }

    @SubscribeEvent
    public void onSetGroupPermissionEvent(final IslandEvents.SetGroupPermission event) {
        final CompoundTag itemData = event.getClickedItem().getOrCreateTagElement(SkyblockAddonCore.MOD_ID);
        final CompoundTag guiData = event.getHolder().getData();

        if(!guiData.contains("group_id") || !itemData.contains("permission_id")) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final UUID groupUUID = guiData.getUUID("group_id");
        final String permissionId = itemData.getString("permission_id");

        event.getIsland().getGroup(groupUUID).inversePermission(permissionId);
        event.getHolder().update();
        event.setResult(Event.Result.ALLOW);
    }

}
