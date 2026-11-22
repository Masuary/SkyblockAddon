package yorickbm.skyblockaddon.events.Gui;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.SpawnPointPlacement;
import yorickbm.skyblockaddon.core.util.UsernameCache;
import yorickbm.skyblockaddon.events.IslandEvents;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.islands.OwnershipTransferConfirmation;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.FunctionRegistry;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class IslandGuiEvents {
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

        if (!IslandActionService.canSetSpawn(event.getIsland(), event.getTarget())) {
            event.getHolder().close();
            event.getTarget().sendMessage(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString("island.setspawn.not.allowed")
            ).withStyle(ChatFormatting.RED),event.getTarget().getUUID());

            event.setResult(Event.Result.DENY);
            return;
        }

        final SpawnPointPlacement placement = IslandActionService.checkSpawnPlacement(event.getIsland(), event.getTarget());
        if(!placement.allowed()) {
            event.getTarget().sendMessage(new TextComponent(
                    SkyBlockAddonLanguage.getLocalizedString(placement.languageKey())
            ).withStyle(ChatFormatting.RED),event.getTarget().getUUID());

            event.setResult(Event.Result.DENY);
            return;
        }

        event.getIsland().setSpawnPoint(ForgeConverter.forgeToInternalVec3i(event.getTarget().blockPosition()));
        event.getHolder().update();
    }

    @SubscribeEvent
    public void onChangeIslandVisiblityEvent(final IslandEvents.ChangeVisibility event) {
        final IslandActionResult result = IslandActionService.toggleVisibility(event.getTarget(), event.getIsland());
        if (!result.succeeded()) {
            denyWithMessage(event, result, "");
            return;
        }
        event.getHolder().update();
    }

    @SubscribeEvent
    public void onTravelToIslandEvent(final IslandEvents.TravelToIsland event) {
        final CompoundTag modData = event.getClickedItem().getOrCreateTagElement(SkyblockAddonCore.MOD_ID);

        if(!modData.contains(GuiDataKeys.ISLAND_ID)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final ForgeIsland island = (ForgeIsland) IslandManager.getInstance().getIslandByUUID(modData.getUUID(GuiDataKeys.ISLAND_ID));

        //Island is not found
        if(island == null) {
            event.setResult(Event.Result.DENY);
            return;
        }

        event.getHolder().close();
        if (!island.isVisible() && !island.isPartOf(event.getTarget().getUUID()) && !event.getTarget().hasPermissions(Commands.LEVEL_MODERATORS)) {
            event.getTarget().sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.travel.private")).withStyle(ChatFormatting.RED), event.getTarget().getUUID());
            event.setResult(Event.Result.DENY);
            return;
        }
        if (IslandActionService.isBannedFrom(island, event.getTarget())) {
            event.getTarget().sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.banned.travel")).withStyle(ChatFormatting.RED), event.getTarget().getUUID());
            event.setResult(Event.Result.DENY);
            return;
        }

        island.teleportTo(event.getTarget());
        event.getTarget().sendMessage(new TextComponent(
                SkyBlockAddonLanguage.getLocalizedString("island.travel")
        ).withStyle(ChatFormatting.GREEN),event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onInviteNewMemberEvent(final IslandEvents.InviteNewMember event) {
        final CompoundTag guiData = event.getHolder().getData();

        if(!guiData.contains(GuiDataKeys.GROUP_ID)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        if (!IslandActionService.canManage(event.getIsland(), event.getTarget())) {
            denyWithMessage(event, IslandActionResult.NOT_ALLOWED, "");
            return;
        }

        event.getHolder().close();
        event.getTarget().sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.click.to.add"))
                        .withStyle(ChatFormatting.GREEN)
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/island group \""
                                        +  event.getIsland().getGroup(guiData.getUUID(GuiDataKeys.GROUP_ID)).getName()
                                        + "\" addMember "
                                        + event.getTarget().getName().getString().trim())))
                ,event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onCreateNewGroupEvent(final IslandEvents.CreateNewGroup event) {
        if (!IslandActionService.canManage(event.getIsland(), event.getTarget())) {
            denyWithMessage(event, IslandActionResult.NOT_ALLOWED, "");
            return;
        }
        event.getHolder().close();

        final UUID id = UUID.randomUUID();
        final Function<ServerPlayer, Boolean> function = executor -> {
            final ItemStack heldItem = executor.getMainHandItem().copy();
            final String newGroupName = ForgeIslandGroup.nameOf(heldItem);
            final IslandActionResult result = IslandActionService.createGroupFromHeldItem(executor, event.getIsland());
            if (!result.succeeded()) {
                IslandActionService.sendFailure(executor, result, newGroupName);
                return result != IslandActionResult.HELD_ITEM_MISSING && result != IslandActionResult.GROUP_NAME_EXISTS;
            }

            ServerHelper.playSongToPlayer(executor, SoundEvents.NOTE_BLOCK_CHIME, SkyblockAddonCore.UI_SUCCESS_VOL, 1f);
            executor.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.group.created")
                            .formatted(newGroupName, Objects.requireNonNull(heldItem.getItem().getRegistryName()).getPath()))
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
        if (closeUnlessAllowedToManage(event)) return;

        final String biome = modData.getString("biome");
        event.getIsland().updateBiome(biome, event.getTarget().server);
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

        if(!guiData.contains(GuiDataKeys.PLAYER_ID)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final UUID playerUUID = guiData.getUUID(GuiDataKeys.PLAYER_ID);
        final String playerName = UsernameCache.getBlocking(playerUUID);
        final IslandActionResult result = IslandActionService.kickMember(event.getTarget(), event.getIsland(), playerUUID);
        event.getHolder().close();
        if (!result.succeeded()) {
            denyWithMessage(event, result, playerName);
            return;
        }

        event.getTarget().sendMessage(
                new TextComponent(
                        SkyBlockAddonLanguage.getLocalizedString("island.member.kicked")
                                .formatted(playerName)
                ).withStyle(ChatFormatting.GREEN),
                event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onSetPlayerGroupEvent(final IslandEvents.SetPlayerGroup event) {
        final CompoundTag modData = event.getClickedItem().getOrCreateTag();
        final CompoundTag guiData = event.getHolder().getData().getCompound(SkyblockAddonCore.MOD_ID);

        if(!guiData.contains(GuiDataKeys.PLAYER_ID) || !modData.contains(GuiDataKeys.GROUP_ID)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final UUID groupUUID = modData.getUUID(GuiDataKeys.GROUP_ID);
        final UUID playerUUID = guiData.getUUID(GuiDataKeys.PLAYER_ID);
        if (event.getIsland().isDemotionToVisitors(playerUUID, groupUUID)) {
            final IslandActionResult blocker = IslandActionService.checkMoveToVisitors(event.getTarget(), event.getIsland(), playerUUID);
            if (blocker != IslandActionResult.SUCCESS) {
                denyWithMessage(event, blocker, UsernameCache.getBlocking(playerUUID));
                return;
            }
            askToConfirmMoveToVisitors(event, groupUUID);
            return;
        }
        assignGroupFromGui(event, playerUUID, groupUUID);
    }

    /**
     * Moving a member to Visitors removes them from the island, so the chest GUI asks first, like the enhanced screen.
     */
    private static void askToConfirmMoveToVisitors(final IslandEvents.SetPlayerGroup event, final UUID visitorsGroupId) {
        final CompoundTag confirmData = event.getHolder().getData().copy();
        final CompoundTag modData = confirmData.getCompound(SkyblockAddonCore.MOD_ID);
        modData.putUUID(GuiDataKeys.GROUP_ID, visitorsGroupId);
        confirmData.put(SkyblockAddonCore.MOD_ID, modData);
        GUILibraryRegistry.openGUIForPlayer(event.getTarget(), "skyblockaddon:confirm_move_to_visitors", confirmData);
    }

    @SubscribeEvent
    public void onConfirmMoveToVisitors(final IslandEvents.ConfirmMoveToVisitors event) {
        final CompoundTag guiData = event.getHolder().getData().getCompound(SkyblockAddonCore.MOD_ID);
        if (event.getIsland() == null || !guiData.contains(GuiDataKeys.PLAYER_ID) || !guiData.contains(GuiDataKeys.GROUP_ID)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        assignGroupFromGui(event, guiData.getUUID(GuiDataKeys.PLAYER_ID), guiData.getUUID(GuiDataKeys.GROUP_ID));
    }

    private static void assignGroupFromGui(final IslandEvents event, final UUID playerUUID, final UUID groupUUID) {
        final String playerName = UsernameCache.getBlocking(playerUUID);
        final IslandActionResult result = IslandActionService.assignGroup(event.getTarget(), event.getIsland(), playerUUID, groupUUID);
        if(!result.succeeded()) {
            denyWithMessage(event, result, playerName);
            return;
        }
        event.getHolder().close();

        event.getTarget().sendMessage(
                IslandActionService.groupAssignedMessage(result, playerName, event.getIsland().getGroup(groupUUID).getName()),
                event.getTarget().getUUID());
    }

    @SubscribeEvent
    public void onRemoveGroup(final IslandEvents.RemoveGroup event) {
        final CompoundTag guiData = event.getHolder().getData();
        if(!guiData.contains(GuiDataKeys.GROUP_ID)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        final UUID groupUUID = guiData.getUUID(GuiDataKeys.GROUP_ID);
        final ForgeIslandGroup group = (ForgeIslandGroup) event.getIsland().getGroup(groupUUID);
        event.getHolder().close();
        if (group == null) {
            denyWithMessage(event, IslandActionResult.GROUP_NOT_FOUND, "");
            return;
        }
        final String groupName = group.getName();

        final IslandActionResult result = IslandActionService.removeGroup(event.getTarget(), event.getIsland(), groupUUID);
        if(result.succeeded()) {
            event.getTarget().sendMessage(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("island.group.remove.success"), groupName))
                            .withStyle(ChatFormatting.RED)
                    ,event.getTarget().getUUID());
            event.setResult(Event.Result.ALLOW);
            return;
        }

        if (result == IslandActionResult.NO_CHANGE) {
            event.getTarget().sendMessage(new TextComponent(String.format(SkyBlockAddonLanguage.getLocalizedString("island.group.remove.failed"), groupName))
                            .withStyle(ChatFormatting.RED)
                    ,event.getTarget().getUUID());
            event.setResult(Event.Result.DENY);
            return;
        }
        denyWithMessage(event, result, groupName);
    }

    @SubscribeEvent
    public void onTransferOwnershipEvent(final IslandEvents.TransferOwnership event) {
        final Optional<UUID> newOwner = clickedPlayerId(event);
        if (newOwner.isEmpty()) {
            event.setResult(Event.Result.DENY);
            return;
        }
        event.getHolder().close();
        OwnershipTransferConfirmation.offer(event.getTarget(), event.getIsland(), newOwner.get());
    }

    @SubscribeEvent
    public void onExpelVisitorEvent(final IslandEvents.ExpelVisitor event) {
        final Optional<UUID> visitorId = clickedPlayerId(event);
        if (visitorId.isEmpty()) {
            event.setResult(Event.Result.DENY);
            return;
        }
        final String visitorName = UsernameCache.getBlocking(visitorId.get());
        final ServerPlayer visitor = Objects.requireNonNull(event.getTarget().getServer()).getPlayerList().getPlayer(visitorId.get());
        final IslandActionResult result = visitor == null
                ? IslandActionResult.TARGET_NOT_ON_ISLAND
                : IslandActionService.expelVisitor(event.getTarget(), event.getIsland(), visitor);
        if (!result.succeeded()) {
            denyWithMessage(event, result, visitorName);
            return;
        }
        event.getTarget().sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.expel.success").formatted(visitorName))
                .withStyle(ChatFormatting.GREEN), event.getTarget().getUUID());
        event.getHolder().update();
    }

    @SubscribeEvent
    public void onBanPlayerEvent(final IslandEvents.BanPlayer event) {
        final Optional<UUID> playerId = clickedPlayerId(event);
        if (playerId.isEmpty()) {
            event.setResult(Event.Result.DENY);
            return;
        }
        final String playerName = UsernameCache.getBlocking(playerId.get());
        final IslandActionResult result = IslandActionService.banPlayer(event.getTarget(), event.getIsland(), playerId.get());
        if (!result.succeeded()) {
            denyWithMessage(event, result, playerName);
            return;
        }
        event.getTarget().sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.success").formatted(playerName))
                .withStyle(ChatFormatting.GREEN), event.getTarget().getUUID());
        event.getHolder().update();
    }

    @SubscribeEvent
    public void onUnbanPlayerEvent(final IslandEvents.UnbanPlayer event) {
        final Optional<UUID> playerId = clickedPlayerId(event);
        if (playerId.isEmpty()) {
            event.setResult(Event.Result.DENY);
            return;
        }
        final String playerName = UsernameCache.getBlocking(playerId.get());
        final IslandActionResult result = IslandActionService.unbanPlayer(event.getTarget(), event.getIsland(), playerId.get());
        if (!result.succeeded()) {
            denyWithMessage(event, result, playerName);
            return;
        }
        event.getTarget().sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.unban.success").formatted(playerName))
                .withStyle(ChatFormatting.GREEN), event.getTarget().getUUID());
        event.getHolder().update();
    }

    private static Optional<UUID> clickedPlayerId(final IslandEvents event) {
        final CompoundTag itemData = event.getClickedItem().getOrCreateTagElement(SkyblockAddonCore.MOD_ID);
        if (!itemData.hasUUID(GuiDataKeys.PLAYER_ID)) return Optional.empty();
        return Optional.of(itemData.getUUID(GuiDataKeys.PLAYER_ID));
    }

    private static void denyWithMessage(final IslandEvents event, final IslandActionResult result, final String subject) {
        IslandActionService.sendFailure(event.getTarget(), result, subject);
        event.setResult(Event.Result.DENY);
    }

    /**
     * Clicks in a manager menu re-check the right the menu was opened with, since it may be gone by now.
     */
    private static boolean closeUnlessAllowedToManage(final IslandEvents event) {
        if (IslandActionService.canManage(event.getIsland(), event.getTarget())) return false;
        event.getHolder().close();
        denyWithMessage(event, IslandActionResult.NOT_ALLOWED, "");
        return true;
    }

    @SubscribeEvent
    public void onSetGroupPermissionEvent(final IslandEvents.SetGroupPermission event) {
        final CompoundTag itemData = event.getClickedItem().getOrCreateTagElement(SkyblockAddonCore.MOD_ID);
        final CompoundTag guiData = event.getHolder().getData();

        if(!guiData.contains(GuiDataKeys.GROUP_ID) || !itemData.contains("permission_id")) {
            event.setResult(Event.Result.DENY);
            return;
        }
        if (closeUnlessAllowedToManage(event)) return;

        final UUID groupUUID = guiData.getUUID(GuiDataKeys.GROUP_ID);
        final String permissionId = itemData.getString("permission_id");

        event.getIsland().getGroup(groupUUID).inversePermission(permissionId);
        event.getHolder().update();
        event.setResult(Event.Result.ALLOW);
    }

}
