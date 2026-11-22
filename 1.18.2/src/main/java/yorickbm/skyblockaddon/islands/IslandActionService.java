package yorickbm.skyblockaddon.islands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandActionResult;
import yorickbm.skyblockaddon.core.islands.IslandActions;
import yorickbm.skyblockaddon.core.islands.IslandActor;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.util.PlayerNames;

import java.util.Objects;
import java.util.UUID;

/**
 * Runs the island actions for both GUI stacks and the commands: the rights and rules live in {@link IslandActions},
 * this adds the server side effects (caches, teleports, notifications of the affected player).
 */
public final class IslandActionService {

    private IslandActionService() {
    }

    public static IslandActor actorOf(final ServerPlayer player) {
        return new IslandActor(player.getUUID(), player.hasPermissions(Commands.LEVEL_GAMEMASTERS));
    }

    public static boolean canManage(final Island island, final ServerPlayer player) {
        return IslandActions.canManage(island, actorOf(player));
    }

    /**
     * Only the owner or a server admin (the level the legacy Set spawn button has always required) moves the island spawn.
     */
    public static boolean canSetSpawn(final Island island, final ServerPlayer player) {
        return island.isOwner(player.getUUID()) || player.hasPermissions(Commands.LEVEL_ADMINS);
    }

    /**
     * A member moved to Visitors is removed from the island like a kick and told so.
     */
    public static IslandActionResult assignGroup(final ServerPlayer actor, final ForgeIsland island, final UUID target, final UUID groupId) {
        final IslandActionResult result = IslandActions.assignGroup(island, actorOf(actor), target, groupId);
        if (result != IslandActionResult.MEMBER_REMOVED) return result;

        afterMemberRemoved(actor, island, target);
        notifyIfOnline(server(actor), target, SkyBlockAddonLanguage.getLocalizedString("island.member.demoted.notify")
                .formatted(PlayerNames.nameOf(server(actor), island.getOwner())), ChatFormatting.RED);
        return result;
    }

    /**
     * The actor's message after a successful group assignment: the group set, or the member removed by a move to Visitors.
     */
    public static MutableComponent groupAssignedMessage(final IslandActionResult result, final String playerName, final String groupName) {
        final String message = result == IslandActionResult.MEMBER_REMOVED
                ? SkyBlockAddonLanguage.getLocalizedString(result.languageKey()).formatted(playerName)
                : SkyBlockAddonLanguage.getLocalizedString("island.member.group.set").formatted(playerName, groupName);
        return new TextComponent(message).withStyle(ChatFormatting.GREEN);
    }

    public static IslandActionResult createGroupFromHeldItem(final ServerPlayer actor, final Island island) {
        final ItemStack heldItem = actor.getMainHandItem();
        if (heldItem.isEmpty()) return IslandActionResult.HELD_ITEM_MISSING;
        return IslandActions.createGroup(island, actorOf(actor), new ForgeIslandGroup(UUID.randomUUID(), heldItem, false));
    }

    public static IslandActionResult removeGroup(final ServerPlayer actor, final Island island, final UUID groupId) {
        return IslandActions.removeGroup(island, actorOf(actor), groupId);
    }

    public static IslandActionResult toggleVisibility(final ServerPlayer actor, final Island island) {
        return IslandActions.toggleVisibility(island, actorOf(actor));
    }

    public static IslandActionResult kickMember(final ServerPlayer actor, final ForgeIsland island, final UUID target) {
        final IslandActionResult result = IslandActions.kickMember(island, actorOf(actor), target);
        if (!result.succeeded()) return result;

        afterMemberRemoved(actor, island, target);
        return result;
    }

    private static void afterMemberRemoved(final ServerPlayer actor, final ForgeIsland island, final UUID target) {
        IslandManager.getInstance().clearCacheForPlayer(target);
        island.sendToWorldSpawnIfStandingOn(server(actor), target);
    }

    public static IslandActionResult transferOwnership(final ServerPlayer actor, final ForgeIsland island, final UUID newOwner) {
        final IslandActionResult result = IslandActions.transferOwnership(island, actorOf(actor), newOwner);
        if (!result.succeeded()) return result;

        IslandManager.getInstance().clearCacheForPlayer(actor.getUUID());
        IslandManager.getInstance().clearCacheForPlayer(newOwner);
        notifyIfOnline(server(actor), newOwner, SkyBlockAddonLanguage.getLocalizedString("commands.transfer.received")
                .formatted(actor.getDisplayName().getString()), ChatFormatting.GREEN);
        return result;
    }

    public static IslandActionResult expelVisitor(final ServerPlayer actor, final ForgeIsland island, final ServerPlayer target) {
        final IslandActionResult result = IslandActions.checkExpel(island, actorOf(actor), target.getUUID());
        if (!result.succeeded()) return result;
        if (!island.isStandingOn(target)) return IslandActionResult.TARGET_NOT_ON_ISLAND;

        ForgeIsland.sendToWorldSpawn(target);
        target.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.expel.notify")
                .formatted(PlayerNames.nameOf(server(actor), island.getOwner()))).withStyle(ChatFormatting.RED), target.getUUID());
        return result;
    }

    public static IslandActionResult banPlayer(final ServerPlayer actor, final ForgeIsland island, final UUID target) {
        final IslandActionResult result = IslandActions.banPlayer(island, actorOf(actor), target);
        if (!result.succeeded()) return result;

        final MinecraftServer server = server(actor);
        final ServerPlayer bannedPlayer = server.getPlayerList().getPlayer(target);
        if (bannedPlayer == null) return result;

        if (isBannedFrom(island, bannedPlayer) && island.isStandingOn(bannedPlayer)) ForgeIsland.sendToWorldSpawn(bannedPlayer);
        bannedPlayer.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("commands.ban.notify")
                .formatted(PlayerNames.nameOf(server, island.getOwner()))).withStyle(ChatFormatting.RED), target);
        return result;
    }

    public static IslandActionResult unbanPlayer(final ServerPlayer actor, final Island island, final UUID target) {
        return IslandActions.unbanPlayer(island, actorOf(actor), target);
    }

    /**
     * Operators are never held back by an island ban so staff can still inspect reported islands.
     */
    public static boolean isBannedFrom(final Island island, final ServerPlayer player) {
        return island.isBanned(player.getUUID()) && !player.hasPermissions(Commands.LEVEL_GAMEMASTERS);
    }

    /**
     * The failure text of a result. {@code subject} fills the %s of messages that name a player or group.
     */
    public static MutableComponent failureMessage(final IslandActionResult result, final String subject) {
        return new TextComponent(SkyBlockAddonLanguage.getLocalizedString(result.languageKey()).formatted(subject)).withStyle(ChatFormatting.RED);
    }

    public static void sendFailure(final ServerPlayer player, final IslandActionResult result, final String subject) {
        player.sendMessage(failureMessage(result, subject), player.getUUID());
    }

    private static void notifyIfOnline(final MinecraftServer server, final UUID playerId, final String message, final ChatFormatting color) {
        final ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) player.sendMessage(new TextComponent(message).withStyle(color), playerId);
    }

    private static MinecraftServer server(final ServerPlayer player) {
        return Objects.requireNonNull(player.getServer(), "Island actions run on the server");
    }
}
